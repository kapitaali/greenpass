package com.example;

import COSE.*;
import com.upokecenter.cbor.CBORObject;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import COSE.*;
import com.upokecenter.cbor.CBORObject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;


/*
How this works 

    Dependencies: We rely heavily on com.augustcellars.cose for the heavy lifting of cryptographic signing. This is the industry-standard library for Java COSE implementation.

    Key Handling: The parsePrivateKey method uses BouncyCastle (PEMReader) to cleanly convert the raw PEM string from the URL into a Java PrivateKey object valid for EC operations.

    KID Generation: The spec requires the "Key ID" to be the first 8 bytes of the SHA-256 fingerprint of the certificate. This is implemented in calculateKid.

    Payload Structure: The generatePayload method builds the nested map structure. com.upokecenter.cbor (which comes with the cose library) is used to ensure the Integers are encoded correctly as CBOR integers, not strings.

    Compression: We use the standard java.util.zip.Deflater for ZLIB compression.

    Base45: Since Base45 is a niche encoding used almost exclusively for Health Certificates, I included a compact implementation at the bottom of the file to save you from finding a separate jar file for it.

*/

public class CovidEncoder {

    // Internal keys to store PEM data in our map
    private static final String KEY_PEM_ENTRY = "__PRIVATE_KEY__";
    private static final String CERT_PEM_ENTRY = "__CERTIFICATE__";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar covid-encoder.jar <path-to-data-file>");
            System.exit(1);
        }

        try {
            // Register Bouncy Castle for Crypto
            Security.addProvider(new BouncyCastleProvider());

            // 1. Load Data AND Keys from File
            System.out.println("[*] Parsing file: " + args[0]);
            Map<String, String> inputData = loadInputData(args[0]);

            // 2. Validate Keys exist in file
            if (!inputData.containsKey(KEY_PEM_ENTRY) || !inputData.containsKey(CERT_PEM_ENTRY)) {
                throw new RuntimeException("File must contain both -----BEGIN PRIVATE KEY----- and -----BEGIN CERTIFICATE----- sections.");
            }

            // 3. Parse Keys
            System.out.println("[*] Parsing Crypto Keys...");
            PrivateKey privateKey = parsePrivateKey(inputData.get(KEY_PEM_ENTRY));
            byte[] kid = calculateKid(inputData.get(CERT_PEM_ENTRY));

            System.out.println("[*] Calculated Key ID (KID): " + bytesToHex(kid));

            // 4. Construct Payload
            CBORObject payload = generatePayload(inputData);

            // 5. Sign
            byte[] coseBytes = signPayload(payload, privateKey, kid);

            // 6. Compress & Encode
            byte[] compressed = compress(coseBytes);
            String base45 = Base45.getEncoder().encodeToString(compressed);
            String hcert = "HC1:" + base45;
            
            System.out.println("\n==========================================");
            System.out.println("GENERATED COVID PASS (HCERT):");
            System.out.println("==========================================");
            System.out.println(hcert);
            System.out.println("==========================================");

        } catch (Exception e) {
            System.err.println("[!] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parses the file for both key=value pairs AND PEM blocks.
     */
    private static Map<String, String> loadInputData(String path) throws IOException {
        Map<String, String> map = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        
        StringBuilder buffer = new StringBuilder();
        boolean recording = false;
        String recordingType = ""; // "KEY" or "CERT"

        for (String line : lines) {
            String trimmed = line.trim();
            
            // Detect Start of PEM
            if (trimmed.startsWith("-----BEGIN")) {
                recording = true;
                buffer.setLength(0); // Clear buffer
                if (trimmed.contains("PRIVATE KEY")) recordingType = KEY_PEM_ENTRY;
                else if (trimmed.contains("CERTIFICATE")) recordingType = CERT_PEM_ENTRY;
            }

            if (recording) {
                buffer.append(line).append("\n");
                // Detect End of PEM
                if (trimmed.startsWith("-----END")) {
                    recording = false;
                    map.put(recordingType, buffer.toString());
                }
            } else {
                // Standard Key=Value parsing
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split("=", 2);
                if (parts.length == 2) {
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return map;
    }

    private static CBORObject generatePayload(Map<String, String> data) {
        long now = Instant.now().getEpochSecond();
        long exp = now + (365 * 24 * 60 * 60);

        CBORObject root = CBORObject.NewMap();
        root.Add(1, data.getOrDefault("co", "AT")); 
        root.Add(6, now);
        root.Add(4, exp);

        CBORObject hcertMap = CBORObject.NewMap();
        CBORObject v1Map = CBORObject.NewMap();

        CBORObject vArr = CBORObject.NewArray();
        CBORObject vac = CBORObject.NewMap();
        
        vac.Add("ci", data.getOrDefault("ci", "urn:uvci:01:AT:TEST/12345"));
        vac.Add("co", data.getOrDefault("co", "AT"));
        vac.Add("dt", data.getOrDefault("dt", "2023-01-01"));
        vac.Add("is", data.getOrDefault("is", "Ministry of Health, Austria"));
        vac.Add("ma", data.getOrDefault("ma", "ORG-100030215"));
        vac.Add("mp", data.getOrDefault("mp", "EU/1/20/1528"));
        vac.Add("tg", data.getOrDefault("tg", "840539006"));
        vac.Add("vp", data.getOrDefault("vp", "1119349007"));
        vac.Add("dn", Integer.parseInt(data.getOrDefault("dn", "1")));
        vac.Add("sd", Integer.parseInt(data.getOrDefault("sd", "2")));
        vArr.Add(vac);

        CBORObject nam = CBORObject.NewMap();
        nam.Add("fn", data.getOrDefault("fn", "Mustermann"));
        nam.Add("fnt", data.getOrDefault("fnt", "MUSTERMANN"));
        nam.Add("gn", data.getOrDefault("gn", "Erika"));
        nam.Add("gnt", data.getOrDefault("gnt", "ERIKA"));

        v1Map.Add("v", vArr);
        v1Map.Add("nam", nam);
        v1Map.Add("dob", data.getOrDefault("dob", "1990-01-01"));
        v1Map.Add("ver", "1.3.0");

        hcertMap.Add(1, v1Map);
        root.Add(-260, hcertMap);
        return root;
    }

    private static byte[] signPayload(CBORObject payload, PrivateKey privateKey, byte[] kid) throws Exception {
        Sign1Message msg = new Sign1Message();
        msg.SetContent(payload.EncodeToBytes());
        msg.addAttribute(HeaderKeys.Algorithm, AlgorithmID.ECDSA_256.AsCBOR(), Attribute.PROTECTED);
        msg.addAttribute(HeaderKeys.KID, CBORObject.FromObject(kid), Attribute.PROTECTED);
        OneKey oneKey = new OneKey(null, privateKey);
        msg.sign(oneKey);
        return msg.EncodeToBytes();
    }

    private static byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        return baos.toByteArray();
    }

    private static byte[] calculateKid(String certPem) throws Exception {
        // Remove headers/newlines for parsing the Base64 body
        String cleanPem = certPem.replaceAll("-----BEGIN CERTIFICATE-----", "")
                                 .replaceAll("-----END CERTIFICATE-----", "")
                                 .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) fact.generateCertificate(new ByteArrayInputStream(decoded));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fingerprint = digest.digest(cert.getEncoded());
        return Arrays.copyOfRange(fingerprint, 0, 8);
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = reader.readPemObject();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
            KeyFactory factory = KeyFactory.getInstance("EC", "BC");
            return factory.generatePrivate(privKeySpec);
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static class Base45 {
        private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
        private static final Base45 ENCODER = new Base45();
        public static Base45 getEncoder() { return ENCODER; }
        public String encodeToString(byte[] input) {
            StringBuilder res = new StringBuilder();
            for (int i = 0; i < input.length; i += 2) {
                if (input.length - i > 1) {
                    int x = (input[i] & 0xFF) * 256 + (input[i + 1] & 0xFF);
                    res.append(ALPHABET.charAt(x % (45 * 45) % 45))
                       .append(ALPHABET.charAt(x % (45 * 45) / 45))
                       .append(ALPHABET.charAt(x / (45 * 45)));
                } else {
                    int x = input[i] & 0xFF;
                    res.append(ALPHABET.charAt(x % 45))
                       .append(ALPHABET.charAt(x / 45));
                }
            }
            return res.toString();
        }
    }
}

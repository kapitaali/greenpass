package com.example;
import com.example.Base45;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import COSE.*;

public class GreenpassDecoder {
    // CBOR claim keys for CWT structure
    private static final int CLAIM_ISSUER = 1;
    private static final int CLAIM_EXPIRATION = 4;
    private static final int CLAIM_ISSUED_AT = 6;
    private static final int CLAIM_HCERT = -260;
    private static final int HCERT_VERSION_1 = 1;


    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar GreenpassDecoder-...with-dependencies.jar \'HC1:...\'");
            System.exit(1);
        }

        String qrCodeData = args[0];
        validateEUCovidPass(qrCodeData);
    }

/*
            Decoding worked in 2022 as follows:

            QR code --> QR DECODER --> RAW QR-decoded string 
             --> BASE45 decoder --> zlib compressed string --> COSE string 
             --> CBOR decoder --> CBOR string --> CBOR decoder --> final JSON string
*/


    public static void validateEUCovidPass(String qrCodeData) {
        try {
            // RAW QR-string
            if (qrCodeData.startsWith("HC1:")) {
                qrCodeData = qrCodeData.substring(4);
            }

            // Decode Base45
            byte[] base45Decoded = Base45.decode(qrCodeData);
            System.out.println("Base45 decoded length: " + base45Decoded.length);

            // Decompress ZLIB
            byte[] zlibDecompressed = decompressZlib(base45Decoded);
            System.out.println("ZLIB decompressed length: " + zlibDecompressed.length);

            // Decode COSE message
            Message coseMessage = null;
            try {
                coseMessage = Message.DecodeFromBytes(zlibDecompressed);
                System.out.println("Debug: COSE message decoded successfully");
            } catch (Exception e) {
                System.out.println("COSE decoding failed: " + e.getMessage());
            }
            
            if (!(coseMessage instanceof Sign1Message)) {
                System.out.println("Invalid COSE message type - expected Sign1Message");
            }
            
            Sign1Message sign1Message = (Sign1Message) coseMessage;

            // Extract CBOR payload
            byte[] payload = sign1Message.GetContent();
            System.out.println("Debug: CBOR payload extracted, length: " + payload.length);
            
            CBORObject cbor = CBORObject.DecodeFromBytes(payload);

            // The certificate data is in the -260 claim
            System.out.println("-----------------------");
            System.out.println(cbor);
            System.out.println("-----------------------");
            CBORObject hcert = cbor.get(CLAIM_HCERT);
            if (hcert == null || hcert.getType() != CBORType.Map) {
                System.out.println("Invalid certificate structure - missing hcert claim");
            }
            
            // Get version and certificate data (key 1 for eu_dgc_v1)
            CBORObject certData = hcert.get(HCERT_VERSION_1);
            if (certData == null || certData.getType() != CBORType.Map) {
                System.out.println("Invalid certificate structure - missing certificate data");
            }

            System.out.println("Valid EU COVID Pass structure.");
            System.out.println("Payload: " + hcert.ToJSONString());
        } catch (Exception e) {
            System.err.println("Error validating EU COVID Pass: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static byte[] decompressZlib(byte[] compressedData) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        byte[] result = new byte[1024];
        int resultLength = inflater.inflate(result);
        inflater.end();
        byte[] output = new byte[resultLength];
        System.arraycopy(result, 0, output, 0, resultLength);
        return output;
    }
}


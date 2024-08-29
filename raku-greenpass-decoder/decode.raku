#!/usr/bin/env raku
use v6;
use lib $?FILE.IO.dirname;
use Compress::Zlib;
use CBOR::Simple;

=begin pod

=TITLE Raku COVID greenpass decoder

=AUTHOR Teppo Saari

This thing here decodes greenpasses based on their QR code readings. The string starts
 "HC1:..."

Decoding works as follows:

QR code --> QR DECODER --> RAW QR-decoded string 
 --> BASE45 decoder --> zlib compressed string --> COSE string 
 --> CBOR decoder --> CBOR string --> CBOR decoder --> final JSON string

=end pod


sub MAIN (
  Str $hc1string, #= greenpass data string "HC1:..."
  # try for example 'HC1:NCFZ80C80T9WTWGSLKC 49794IJFE8KY5DB4FBB7Z6*70%*8FN0XLCB*1WY01BC20DD97TK0F90KECTHGWJC0FD$F5AIA%G7X+AQB9746NG7QB9ZR62OA+S9ZH9YL6ES85IBP1BEDB9C9WR6GZAG471T8UPC3JCLC9FVCPD0LVC6JD846Y96C463W5VX6+EDS8F8UADZCTOAOPCAECU34F/D6%ECECKPCU34F/DBWENWEBWE-3EN44:+CP+88/DCEC3VCB$D% D3IA4W5646946%96X47.JCP9EJY8L/5M/5546.96D463KC.SC4KCD3DX47B46IL6646H*6Z/ER2DD46JH8946JPCT3E5JDLA7+/68463W5/A6..DX%DZJC4/D5UA QE:DC8KD JCF/D9Z9MWE2DD$N9*KE144+KE:WO50E8ZKW.CAWEITA2OAAB8JH9MPCG/D.PETB8MTA0S7RB8SB96DBJH9CY8EB8$PC5$CUZC$$5Y$527B:W9V%V:/OU OC9PSZKCSMY/AN7BUCHU+R912LHGI-1CO50FUI*1E-OGFQBZEQ8N6%L%7GLJS:WI%NPKYGDUI+TL-5UPYUU M2/4GLI' 
  Bool :$diagnostics #= see key, message and signature in hexadecimal binary
) {
  # raw QR-coded string
  my $testdata = $hc1string; 
  
  # trim away HC1: and decode into base256
  my @decoded = b45decode($testdata.substr(4));

  # create byte blob
  my Blob $decoded_b = Blob.new(@decoded);

  # decompress with zlib
  my $decompressed = uncompress($decoded_b);
  
  # decode CBOR
  my $data = cbor-deco($decompressed);
  
  # if you want to print JSON, you will need to cbor-deco data twice, as per
  # https://stackoverflow.com/questions/68612766/decode-a-base45-string-that-will-lead-to-a-cbor-compressed-file
  my $headers1 = $data.value[0];
  my $headers2 = $data.value[1];
  my $cbor_data = $data.value[2];
  my $signature = $data.value[3];

  say "-----------------------------------------------------------------";
  say "headers1: ";
  say cbor-deco($headers1);
  say "headers2: ";
  say $headers2;
  say "CBOR data: ";
  say cbor-deco($cbor_data);
  say "signature: ";
  say $signature;
  
  if ($diagnostics) { say cbor-diagnostic($decompressed); }

}

sub divmod ($a, $b) {
    my $remainder = $a;
    my $quotient = 0;
    if ($a >= $b) {
        $remainder = $a % $b;
	    $quotient = ($a - $remainder) / $b;
    }
    return $quotient, $remainder;
}

constant %b45 = (
    '0' => 0, '1' => 1, '2' => 2, '3' => 3, '4' => 4, '5' => 5, '6' => 6, '7' => 7, '8' => 8,
    '9' => 9, 'A' => 10, 'B' => 11, 'C' => 12, 'D' => 13, 'E' => 14, 'F' => 15, 'G' => 16,
    'H' => 17, 'I' => 18, 'J' => 19, 'K' => 20, 'L' => 21, 'M' => 22, 'N' => 23, 'O' => 24,
    'P' => 25, 'Q' => 26, 'R' => 27, 'S' => 28, 'T' => 29, 'U' => 30, 'V' => 31, 'W' => 32,
    'X' => 33, 'Y' => 34, 'Z' => 35, ' ' => 36, '$' => 37, '%' => 38, '*' => 39, '+' => 40,
    '-' => 41, '.' => 42, '/' => 43, ':' => 44);
    
constant $BASE45_CHARSET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';

sub b45decode ($str) {
  my @buf = $str.comb.map: { %b45{$_} };
  my $length = @buf.elems;
  my @output;

  loop (my $i = 0; $i < $length; $i+=3) {
       my $x = @buf[$i] + @buf[$i + 1] * 45;
       if ($length - $i >= 3) {
          my ($d, $c) = divmod($x + @buf[$i + 2] * 45 * 45, 256);
          @output.push($d.UInt);
          @output.push($c.UInt);
       } else {
         @output.push($x.UInt);
       }
    }
    return @output;
  };

sub cbor-deco ($x) {
  my $bad  = cbor-decode($x);  
  return $bad;
};
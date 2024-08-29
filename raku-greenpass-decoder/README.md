---
title: Raku COVID greenpass decoder
author: Teppo Saari
date: 2022-06-17
---

# raku-greenpass-decoder
Decode European COVID greenpasses

This thing here decodes greenpasses based on their QR code readings. The string starts
 "HC1:..."

Decoding works as follows:

QR code --> QR DECODER --> RAW QR-decoded string 
 --> BASE45 decoder --> zlib compressed string --> COSE string 
 --> CBOR decoder --> CBOR string --> CBOR decoder --> final JSON string









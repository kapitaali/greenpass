# Kap Greenpass Decoder

If there are no dependency libraries, you may run the `download-deps.sh` to get them.
You'll need Maven installed.

Set the variable `wd` in greenpassDecoder.kap to the directory where this directory is. 
Kap cannot get working directory inside a script yet, so you need to do it manually.

Then in Kap GUI:

```
use("/home/user/greenpass/kap-greenpass-decoder/greenpassDecoder.kap") ⍝ or whatever is your wd

greenpass:decode "HC1...."
```

Note that the native Kap executable does not have JNI. You have to use this through the GUI.

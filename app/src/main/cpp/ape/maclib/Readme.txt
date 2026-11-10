=== Monkey's Audio SDK ===

This SDK provides the tools to incorporate Monkey's Audio into your own projects. Cruising through the examples is probably the easiest way to learn how to use everything. If you use C++, it's recommended that you simply statically link to maclib.lib. If you use another language or want dynamic linkage, you can use the C-style interface of the dll. (see Decompress\Sample 3 for an example)

To use this SDK, you must agree to all the terms of the license agreement:
http://monkeysaudio.com/license.html

If you make any improvements, please consider submitting them back to the Monkey's Audio project.  Contact details here here:
http://monkeysaudio.com/contact.html

To build on Linux using Windows, run WSL from the SDK folder. Then issue these two commands:

cmake .
cmake --build .

Thanks and good luck :)
# About
The Kahoot4J library aims to bring an object oriented way to connect and control a Kahoot user. It was made because of the lack of updated libraries.

# Usage
To use the Kahoot4J library, either clone the repository and include it within your project or download one of the built artifacts from the [releases section](https://github.com/RedstoneTek/Kahoot4J/releases) and include it within your build path.

## Kahoot Challenge Explanation
First we do some JavaScript shenanigans, replace some functions
that we know to be true by "true".

This part of the code was taken from [this repository](https://github.com/wwwg/kahoot.js).

Then we evaluate the challenge (replaced) with the nashorn js engine.


We take the bytes from the challenge and store them.


Then, we get the bytes from the previously obtained session token,
decode them with base64 so get get base64 decoded bytes.


We then use XOR encryption, using the challenge bytes as a mask.


We take the obtained bytes and make a string out of it. That's our ID.
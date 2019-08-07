# SpongePluginVersionChecker
Minimalistinc include for fully automatic version checking agains the sponge ore repository

# How to use

## Add the dependency

To use this with gradle you should be able to just add the following to your **build.gradle**:
```
plugins {
	id 'java'
	id 'com.github.johnrengelman.shadow' version '5.1.0'
}
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
dependencies {
    shadow 'com.github.DosMike:SpongePluginVersionChecker:1.0'
}
```
I don't use gradle so I don't really know how to generate a fat-jar. This build.gradle was the result
of 15 minutes google research, gl;hf

## Usage example

Once the dependency is added you just need to call one method.
It's recommended to call this method after all plugins are loaded by Spoge.
This method checks if the specified config allows version checking. If so, it performs version check on the provided executor.

`conformAuto(instance:Object, configDir:Path, configName:String, asyncExecutor:SpongeExecutorService)`

**instance**: the plugin instance to check for updates, usually `this`   
**configDir**: where to search for the configuration (private plugin directory is recommended)   
**configName**: the name of the config file, if configDir is the public config dir it's recommended to start the filename with the plugin id   
**asyncExecutor**: in order to take load off of the server thread update checking will be performed on this executor (if permitted)   

# License

MIT licensed, see the LICENSE file.
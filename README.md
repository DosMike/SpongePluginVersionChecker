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
    shadow 'com.github.DosMike:SpongePluginVersionChecker:master-SNAPSHOT'
}
//from personal experience use this task to build your jar:
task uberJar(type:ShadowJar, group:'_Plugin', dependsOn:removeOldVersions) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude 'META-INF/**'
    manifest {
        attributes('Implementation-Title': project.name,
                'Implementation-Version': project.version)
    }
    configurations = [project.configurations.shadow]
    //relocate the version checker to avoid problems with different versions of versionChecker in different plugins
    //conveniently the plugin id supports a limited character set that can be used as package name
    relocate('de.dosmike.sponge', 'shadow.dosmike.versionchecker.'+pluginid) {
        //don't relocate my actual plugins - yes, includes are paths!
        include "de/dosmike/sponge/VersionChecker"
        include "de/dosmike/sponge/VersionChecker\$Version"
    }
    from(sourceSets.main.resources)
    from(sourceSets.main.output) //.output would not capture mcmod.info for me
    classifier = null
}

//if you want your plugin to be jitpack-able, you'll have to add the shadow jar as artifact:
artifacts {
    archives uberJar
	...
}
```

## Usage example

Once the dependency is added you just need to call one method.
It's recommended to call this method after all plugins are loaded by Spoge.
This method checks if the specified config allows version checking. If so, it performs version check on the provided executor.

`VersionChecker.conformAuto(instance:Object, configDir:Path, configName:String)`

**instance**: the plugin instance to check for updates, usually `this`   
**configDir**: where to search for the configuration (private plugin directory is recommended)   
**configName**: the name of the config file, if configDir is the public config dir it's recommended to start the filename with the plugin id   

Alternatively you can use
`VersionChecker.setVersionCheckingEnabled(pluginId:String, allowVersionChecking:boolean);`
followed by
`VersionChecker.checkVersion(instance:Object)` or
`VersionChecker.checkPluginVersion(container:PluginContainer)`

**pluginId**: This should be your plugin id  
**allowVersionChecking**: `true` if version checking is allowed. *To follow Ore Guidelines, this value may **ONLY** be set true from a config node like `node.getBoolean(false)`*  
**instance**: Your plugin instance  
**container**: Your plugin container

## Debgging

If the property VerboseVersionChecker is set to true VersionChecker will print additional information to
the console.

# License

MIT licensed, see the LICENSE file.
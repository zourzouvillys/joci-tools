# joci-tools

some basic tooling for manipulating docker/OCI image manifests and implemention of a basic docker registry.

note: if you're opening in eclipse, run `./gradlew eclipse` to generate the APT configuration (and then refresh the gradle project).

[![](https://jitpack.io/v/io.zrz/joci-tools.svg)](https://jitpack.io/#io.zrz/joci-tools)



```
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
  
  dependencies {
    implementation 'io.zrz:joci-tools:master'
  }
  
```
1- first step you need to change the name of your extension module in [build.gradle.kts](../../../../../../build.gradle.kts) inside test-extensions module

2- second step change the value  of your extension inside [Constants](Constatns.kt) 

3- IMPORTANT PLEASE DO NOT USE 
```kotlin
  override val client: HttpClient
        get() = HttpClient(OkHttp) { // OkHttp can not be used inside unit testing 
            // assigning engine may cause bugs
            engine {
                preconfigured = deps.httpClients.default.okhttp
            }
        }
```

SO REMEMBER TO REMOVE THIS FOR TESTING OR REPLACE IT USING ``HttpClient(CIO)``  

4- enjoy unit testing

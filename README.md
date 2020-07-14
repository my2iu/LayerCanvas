# LayerCanvas
Simple bitmap drawing tool


## Build Instructions

LayerCanvas is currently programmed in Java and compiled to JavaScript using GWT. The Maven build tool is used to compile the Java. To build LayerCanvas, simply run the Maven command

```
mvn package
```

Maven will then build the project. The generated JavaScript will be found in the `target/LayerCanvas-...` directory. If you want Maven to start web server so that you can immediately run the code in a browser, you can give the command

```
mvn gwt:devmode
```

A window will open with instructions on where to point your browser to run the code.


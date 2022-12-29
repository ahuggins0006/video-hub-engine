# video-hub

This project intends to provide an interface for changing the layout of a video hub to create specific scenes that can then be reloaded as needed during runtime. It utilizes the provided tcp connection to update layouts and request status from a video-hub at the specified ip adddress and port.
This project composes a number of libraries to provide a solution.
* aleph
* gloss
* manifold
* timbre
* cljfx

Aleph, gloss, and manifold are used in combination to provide tcp interaction between video-hub-engine and the video hub. Timbre is used for logging. Cljfx is used as for the GUI components. Aleph was chosen for the tcp client because prior experimentation with tcp libraries showed that aleph, gloss, and manifold together provide the most flexibility as far as configuration. Timbre was chosen as the logging solution because it is written purely Clojure and configurable using pure Clojure. Cljfx is used since it is a declarative wrapper using pure Clojure. There abundance of openjfx dependencies are due to cross-platform compatibility of uberjars and graphical libraries.

## Installation

Clone the repo and run 'lein uberjar' to generate a runnable executable.

## Usage

After generating an uberjar with leinegen run the following command

    $ java -jar video-hub-0.1.0-standalone.jar

...

![Screenshot_video_hub_gui](https://user-images.githubusercontent.com/2482105/176328763-2e57aa94-21ea-4ba9-8f62-47225ac9f3ef.png)

### Buttons
#### OPEN config... allows user to use the file chooser to select a configuration file. Configuration file use the EDN data format.
#### CONNECT attempts to establish a connection with a video-hub via ip and port as specified in the configuration file.
#### INPUT, OUTPUT UPDATE route are used in conjuction to update a single route of the current layout.
#### SEND layout is used to send the layout specified in the configuration file to override the current layout of the video hub.
#### SAVE current to scene allows the user to save the current layout as a scene that is labled with a button under the SAVED SCENES section
#### SAVE configuration allows the user to save the current configuration.
#### EXIT allows the user to terminate the application.

### Sample Configuration File
The application relies on a configuration file to provide
* A default layout, the one in the sample provided will reset the video hub to a one-to-one layout configuration
* Connections parameters so that the video hub engine application may communicate with the video hub
* A list of saved scenes. Scenes are a map of their name and their file location.
* Layout -> the overall mapping of inputs to outputs for the video hub
* Scene -> user defined layout; a named layout for future reference.
* Note about scenes: the application will manage generation and referencing of scene files for the user. If the user
  has scene files from another video hub engine instance they may be added to the scenes section of the configuration as needed.

![Screenshot_sample_config](https://user-images.githubusercontent.com/2482105/177677154-6389c6cd-b67f-4e4b-a41d-f4d41868c56f.png)


### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License


This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

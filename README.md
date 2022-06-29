# video-hub

This project intends to provide an interface for changing the layout of a video hub to create specific scenes that can then be reloaded as needed during runtime.

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

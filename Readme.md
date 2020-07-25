## What is this?

You want to start something as a (windows) service (so headless, wihtoud a window) but you need to interact witht he console later? Yeah this is Screen and this should fix that. You start the screen server in the background and can then start a programm piped through the server so you can read its output later. Right now there is also a web part added so you can manage all added programms.

## Execution

`java -Doption1=value1 -Doption2=value2 -jar screen4windwors.jar <command>`


## Commands

### help

WIll display all available commands

### screen 

starts the Screen Service. 

### run <programm> <args>

runs the a programm with the provided args.

## Startoptions
### screen.webservice.port
Default: 8087

Used to change the Port were the webside is running

### screen.port
Default: 9153

Port were the screen server is running. Als screen client will connect to this port and communicate via it with the server

### screen.webservice.need_authentication
Default: false

If set to true you need Username and Password to access the site. 

### screen.webservice.auth_file
Default: users.txt

Name of the file used for authentification. Per line there is a `user=password` entry, the Password must be a SHA256 hash in Hex numbers.

### application.name
Default: the command used to run this program

This is the Name of the program which will be used for displaying.

### screen.webservice.ssl.file
Default: "./testkey.jks"

This is the Path of the Keystore file used for the HTTPS SSL connection. You can generate the keystore using `<path to java>/bin/keystore.exe -genkeypair -keyalg RSA -alias self_signed -keypass <password> -keystore lig.keystore -storepass <password>`

### screen.webservice.ssl.password"
Default: password
This is the password used the open the KeyStore file, read the screen.webservice.ssl.file entry for more information about the keyStore File.

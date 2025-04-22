clear
sudo dpkg -r skyenetapps
./gradlew clean packageLinux
sudo dpkg -i build/jpackage/skyenetapps_1.0.0_amd64.deb
/opt/skyenetapps/bin/SkyenetApps `pwd`

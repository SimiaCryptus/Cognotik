clear
sudo dpkg -r Cognotik
./gradlew clean packageLinux
sudo dpkg -i build/jpackage/cognotik_*.deb
/opt/cognotik/bin/Cognotik `pwd`

clear
./demo-site/encrypt.sh
npx http-server . -p 6981 2>&1 >server.log &
pause 5
browse "http://127.0.0.1:6981/?url=http%3A%2F%2F127.0.0.1%3A6981%2Fdemo-site%2Findex.ssec"
echo "Use the password 'secret'"

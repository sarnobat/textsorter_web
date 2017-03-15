#open "index.html" || xdg-open "index.html"

cd .groovy/lib
mkdir -p ~/.groovy/lib
cp *jar ~/.groovy/lib/
cd ../..
set -m
groovy webserver_json.groovy &
sleep 3
open "index.html" || xdg-open "index.html"
fg %1

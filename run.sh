open "index.html" || xdg-open "index.html"

cd .groovy/lib
mkdir -p ~/.groovy/lib
cp *jar ~/.groovy/lib/
cd ../..

groovy webserver_json.groovy

cat - \
  | perl -pe 's{(<h[0-9]>.*?</h[0-9]>)}{</div>\n<div style="background-color : #FDFD96; margin: 25px; border-radius: 15px; box-shadow: inset 0 0 9px #222222, 10px 10px 14px #999999; padding : 50px; ">\n$1}g' 

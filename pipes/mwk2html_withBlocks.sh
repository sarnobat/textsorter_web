cat - \
  | perl -pe 's{^==\s+(.*)\s+==}{<h2>$1</h2>}g' \
  | perl -pe 's{^==\s+==}{<h2>(blank)</h2>}g' \
  | perl -pe 's{^===\s+(.*)\s+===}{<h3>$1</h3>}g' \
  | perl -pe 's{^===\s+===}{<h3>(blank)</h3>}g' \
  | perl -pe 's{^====\s+(.*)\s+====}{<h4>$1</h4>}g' \
  | perl -pe 's{^=====\s+(.*)\s+=====}{<h5>$1</h5>}g' \
  | perl -pe 's{\n}{<br>\n}g'


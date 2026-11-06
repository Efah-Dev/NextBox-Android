// The NextBox Go packages used by the Android app: nbcore (mobile/, the API
// bound into libbox.aar) and the data store, share-link parser, subscription
// fetcher and sing-box config builder it is built on (internal/).
module nextbox

go 1.25.0

require (
	golang.org/x/sys v0.46.0
	gopkg.in/yaml.v3 v3.0.1
)

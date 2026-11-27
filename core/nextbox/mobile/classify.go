package nbcore

import (
	"encoding/json"

	"nextbox/internal/link"
)

// Classify tells what a scanned QR code, the clipboard or a shared text holds
// (see link.Classify, shared with NextBox for Windows), as JSON.
func Classify(text string) string {
	b, _ := json.Marshal(link.Classify(text))
	return string(b)
}

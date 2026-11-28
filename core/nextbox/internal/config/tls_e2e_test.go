package config

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"io"
	"math/big"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"nextbox/internal/link"
	"nextbox/internal/store"
)

// Traffic goes through a VLESS node over TLS with a browser fingerprint
// (uTLS) and through a Trojan node with plain TLS: the core's TLS client
// works with the Go it is built with.
func TestTLSNodesEndToEnd(t *testing.T) {
	bin := singBox(t)
	web := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "over tls") }))
	defer web.Close()

	dir := t.TempDir()
	certFile, keyFile := selfSigned(t, dir)
	const uuid = "11111111-2222-3333-4444-555555555555"
	vlessPort, trojanPort := freePort(t), freePort(t)
	tlsIn := map[string]any{"enabled": true, "server_name": "localhost", "certificate_path": certFile, "key_path": keyFile}
	stopServer := runCore(t, bin, map[string]any{
		"log": map[string]any{"level": "error"},
		"inbounds": []any{
			map[string]any{"type": "vless", "listen": "127.0.0.1", "listen_port": vlessPort, "users": []any{map[string]any{"uuid": uuid}}, "tls": tlsIn},
			map[string]any{"type": "trojan", "listen": "127.0.0.1", "listen_port": trojanPort, "users": []any{map[string]any{"password": "pw"}}, "tls": tlsIn},
		},
		"outbounds": []any{map[string]any{"type": "direct"}},
	}, vlessPort)
	defer stopServer()

	var nodes []store.Profile
	for i, l := range []string{
		"vless://" + uuid + "@127.0.0.1:" + strconv.Itoa(vlessPort) + "?security=tls&sni=localhost&fp=chrome&allowInsecure=1&type=tcp#v",
		"trojan://pw@127.0.0.1:" + strconv.Itoa(trojanPort) + "?sni=localhost&allowInsecure=1#t",
	} {
		n, err := link.Parse(l)
		if err != nil {
			t.Fatal(err)
		}
		b, _ := json.Marshal(n.Outbound)
		var ob map[string]any
		json.Unmarshal(b, &ob)
		nodes = append(nodes, store.Profile{ID: int64(i + 1), Name: n.Name, Type: n.Type(), Outbound: ob})
	}
	testPort := freePort(t)
	cfg, err := BuildTest(store.DefaultSettings(), nodes, nil, nil, "127.0.0.1:"+strconv.Itoa(freePort(t)), "secret", testPort)
	if err != nil {
		t.Fatal(err)
	}
	stop := runCore(t, bin, cfg, testPort)
	defer stop()

	for _, n := range nodes {
		proxy := &url.URL{Scheme: "http", User: url.UserPassword(TestUser(n.ID), "secret"), Host: "127.0.0.1:" + strconv.Itoa(testPort)}
		c := &http.Client{Timeout: 10 * time.Second, Transport: &http.Transport{Proxy: http.ProxyURL(proxy)}}
		resp, err := c.Get(web.URL)
		if err != nil {
			t.Fatalf("%s: %v", n.Name, err)
		}
		b, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		if string(b) != "over tls" {
			t.Errorf("%s: got %q", n.Name, b)
		}
	}
}

// selfSigned writes a certificate and key for "localhost" into dir.
func selfSigned(t *testing.T, dir string) (certFile, keyFile string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tpl := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "localhost"}, DNSNames: []string{"localhost"},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour),
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	kb, _ := x509.MarshalECPrivateKey(key)
	certFile, keyFile = filepath.Join(dir, "cert.pem"), filepath.Join(dir, "key.pem")
	os.WriteFile(certFile, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0o644)
	os.WriteFile(keyFile, pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: kb}), 0o600)
	return certFile, keyFile
}

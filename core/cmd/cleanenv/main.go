// Command cleanenv runs a program without the hidden "=C:=C:\..." variables
// Windows keeps in every process environment. gomobile panics on them
// ("malformed env var"), so build-core.ps1 starts it through this wrapper.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: cleanenv program [args...]")
		os.Exit(2)
	}
	var env []string
	for _, kv := range os.Environ() {
		if !strings.HasPrefix(kv, "=") {
			env = append(env, kv)
		}
	}
	cmd := exec.Command(os.Args[1], os.Args[2:]...)
	cmd.Env = env
	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	if err := cmd.Run(); err != nil {
		if exit, ok := err.(*exec.ExitError); ok {
			os.Exit(exit.ExitCode())
		}
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

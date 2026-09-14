//go:build linux

package main

import (
	"bufio"
	"bytes"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"
)

// Run against a source-built Linux core. No provider, credentials, or external network is used.
func TestSpliceInterruptedTransfer(t *testing.T) {
	core := os.Getenv("MIHOMO_CORE")
	if core == "" {
		t.Fatal("MIHOMO_CORE must name the Linux executable under test")
	}
	for _, inject := range []bool{false, true} {
		t.Run(fmt.Sprintf("EINTR=%v", inject), func(t *testing.T) {
			dir := t.TempDir()
			server, err := net.Listen("tcp4", "127.0.0.1:0")
			if err != nil {
				t.Fatal(err)
			}
			defer server.Close()
			reserved, err := net.Listen("tcp4", "127.0.0.1:0")
			if err != nil {
				t.Fatal(err)
			}
			proxy := reserved.Addr().String()
			port := reserved.Addr().(*net.TCPAddr).Port
			reserved.Close()
			config := fmt.Sprintf("mixed-port: %d\nmode: rule\nlog-level: info\nrules:\n  - DOMAIN-KEYWORD,newapi,DIRECT\n  - MATCH,REJECT\nhosts:\n  stream.newapi.test: 127.0.0.1\n", port)
			if err := os.WriteFile(filepath.Join(dir, "config.yaml"), []byte(config), 0600); err != nil {
				t.Fatal(err)
			}
			logPath, tracePath := filepath.Join(dir, "core.log"), filepath.Join(dir, "trace.log")
			log, err := os.Create(logPath)
			if err != nil {
				t.Fatal(err)
			}
			defer log.Close()
			args := []string{"-f", "-qq", "-e", "trace=splice", "-o", tracePath}
			if inject {
				args = append(args, "-e", "inject=splice:error=EINTR:when=8+17")
			}
			args = append(args, core, "-d", dir, "-f", filepath.Join(dir, "config.yaml"))
			cmd := exec.Command("strace", args...)
			cmd.Stdout, cmd.Stderr = log, log
			cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
			if err := cmd.Start(); err != nil {
				t.Fatal(err)
			}
			defer func() { syscall.Kill(-cmd.Process.Pid, syscall.SIGTERM); cmd.Wait() }()
			deadline := time.Now().Add(5 * time.Second)
			for {
				b, _ := os.ReadFile(logPath)
				if strings.Contains(string(b), "Start initial compatible provider default") {
					break
				}
				if time.Now().After(deadline) {
					t.Fatalf("core not ready: %s", b)
				}
				time.Sleep(20 * time.Millisecond)
			}
			// Provider initialization is asynchronous to listener bind; this fixture starts after initialization.
			time.Sleep(200 * time.Millisecond)
			expected := make([]byte, 512*1024)
			for i := range expected {
				expected[i] = byte(i*31 + i/1024)
			}
			done := make(chan error, 1)
			go func() {
				c, err := server.Accept()
				if err != nil {
					done <- err
					return
				}
				defer c.Close()
				c.SetDeadline(time.Now().Add(15 * time.Second))
				request := make([]byte, 11)
				if _, err = io.ReadFull(c, request); err != nil {
					done <- err
					return
				}
				if string(request) != "one request" {
					done <- fmt.Errorf("request changed")
					return
				}
				for offset := 0; offset < len(expected); offset += 1024 {
					if _, err = c.Write(expected[offset : offset+1024]); err != nil {
						done <- err
						return
					}
					time.Sleep(time.Millisecond)
				}
				done <- nil
			}()
			client, err := net.DialTimeout("tcp4", proxy, 3*time.Second)
			if err != nil {
				t.Fatal(err)
			}
			defer client.Close()
			client.SetDeadline(time.Now().Add(15 * time.Second))
			target := fmt.Sprintf("stream.newapi.test:%d", server.Addr().(*net.TCPAddr).Port)
			fmt.Fprintf(client, "CONNECT %s HTTP/1.1\r\nHost: %s\r\n\r\n", target, target)
			reader := bufio.NewReader(client)
			status, err := reader.ReadString('\n')
			if err != nil || !strings.Contains(status, "200") {
				t.Fatalf("CONNECT: %q %v", status, err)
			}
			for {
				line, err := reader.ReadString('\n')
				if err != nil {
					t.Fatal(err)
				}
				if line == "\r\n" {
					break
				}
			}
			io.WriteString(client, "one request")
			actual, readErr := io.ReadAll(reader)
			server.Close()
			serverErr := <-done
			trace, _ := os.ReadFile(tracePath)
			coreLog, _ := os.ReadFile(logPath)
			if readErr != nil || serverErr != nil || !bytes.Equal(actual, expected) {
				t.Fatalf("bytes=%d/%d read=%v server=%v\n%s", len(actual), len(expected), readErr, serverErr, coreLog)
			}
			if !strings.Contains(string(coreLog), "DomainKeyword(newapi) using DIRECT") {
				t.Fatal("DIRECT rule was not exercised")
			}
			if inject {
				if !bytes.Contains(trace, []byte("INJECTED")) {
					t.Fatal("no syscall was injected")
				}
				for _, direction := range []string{"read", "write"} {
					if !strings.Contains(string(coreLog), "EINTR direction="+direction) {
						t.Fatalf("missing %s interruption coverage", direction)
					}
				}
			}
			t.Logf("exactly one request, %d bytes intact; injected=%d", len(actual), bytes.Count(trace, []byte("INJECTED")))
		})
	}
}

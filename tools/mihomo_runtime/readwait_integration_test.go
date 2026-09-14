//go:build linux

package main

import (
	"bytes"
	"errors"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	B "github.com/metacubex/sing/common/bufio"
	N "github.com/metacubex/sing/common/network"
)

// Exercise the production syscall readers used when encrypted outbounds cannot splice.
// strace faults only the child fixture; no model service or external network is involved.
func TestReadWaitInterrupted(t *testing.T) {
	self, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	for _, protocol := range []string{"TCP", "UDP"} {
		t.Run(protocol, func(t *testing.T) {
			trace := filepath.Join(t.TempDir(), "trace.log")
			fault := "read:error=EINTR:when=8+7"
			if protocol == "UDP" {
				fault = "recvmsg:error=EINTR:when=1+2"
			}
			cmd := exec.Command("strace", "-f", "-qq", "-o", trace, "-e", "trace=read,recvmsg", "-e", "inject="+fault,
				self, "-test.run=^TestReadWaitFixture$/^"+protocol+"$", "-test.v", "-test.timeout=15s")
			output, runErr := cmd.CombinedOutput()
			if runErr != nil {
				t.Fatalf("interrupted %s failed: %v\n%s", protocol, runErr, output)
			}
			data, err := os.ReadFile(trace)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Contains(data, []byte("INJECTED")) || !bytes.Contains(output, []byte("recovered EINTR direction=readwait_")) {
				t.Fatalf("fixture did not cover production %s EINTR branch: %s", protocol, output)
			}
			t.Logf("%s byte-exact transfer passed with %d injected interruptions", protocol, bytes.Count(data, []byte("INJECTED")))
		})
	}
}

func TestReadWaitFixture(t *testing.T) {
	t.Run("TCP", func(t *testing.T) {
		listener, err := net.ListenTCP("tcp4", &net.TCPAddr{IP: net.IPv4(127, 0, 0, 1)})
		if err != nil {
			t.Fatal(err)
		}
		defer listener.Close()
		client, err := net.DialTCP("tcp4", nil, listener.Addr().(*net.TCPAddr))
		if err != nil {
			t.Fatal(err)
		}
		defer client.Close()
		server, err := listener.AcceptTCP()
		if err != nil {
			t.Fatal(err)
		}
		defer server.Close()
		client.SetDeadline(time.Now().Add(10 * time.Second))
		server.SetDeadline(time.Now().Add(10 * time.Second))
		expected := make([]byte, 128*1024)
		for i := range expected {
			expected[i] = byte(i*31 + i/1024)
		}
		done := make(chan error, 1)
		go func() {
			for i := 0; i < len(expected); i += 1024 {
				if _, err := server.Write(expected[i : i+1024]); err != nil {
					done <- err
					return
				}
				time.Sleep(time.Millisecond)
			}
			done <- server.CloseWrite()
		}()
		waiter, ok := B.CreateReadWaiter(client)
		if !ok {
			t.Fatal("syscall reader not selected")
		}
		waiter.InitializeReadWaiter(N.ReadWaitOptions{})
		var actual bytes.Buffer
		for {
			buffer, err := waiter.WaitReadBuffer()
			if errors.Is(err, io.EOF) {
				break
			}
			if err != nil {
				t.Fatalf("received %d bytes: %v", actual.Len(), err)
			}
			actual.Write(buffer.Bytes())
			buffer.Release()
		}
		if err := <-done; err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(actual.Bytes(), expected) {
			t.Fatalf("bytes=%d/%d", actual.Len(), len(expected))
		}
		// EOF and local close must remain distinguishable from a recovered interruption.
		client.Close()
		if _, err := waiter.WaitReadBuffer(); !errors.Is(err, net.ErrClosed) {
			t.Fatalf("closed socket error=%v", err)
		}
	})
	t.Run("UDP", func(t *testing.T) {
		receiver, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
		if err != nil {
			t.Fatal(err)
		}
		defer receiver.Close()
		sender, err := net.DialUDP("udp4", nil, receiver.LocalAddr().(*net.UDPAddr))
		if err != nil {
			t.Fatal(err)
		}
		defer sender.Close()
		waiter, ok := B.CreatePacketReadWaiter(B.NewPacketConn(receiver))
		if !ok {
			t.Fatal("syscall packet reader not selected")
		}
		waiter.InitializeReadWaiter(N.ReadWaitOptions{})
		for i := 0; i < 40; i++ {
			expected := bytes.Repeat([]byte{byte(i)}, i*31) // includes a valid empty datagram
			if _, err := sender.Write(expected); err != nil {
				t.Fatal(err)
			}
			receiver.SetReadDeadline(time.Now().Add(2 * time.Second))
			buffer, from, err := waiter.WaitReadPacket()
			if err != nil {
				t.Fatal(err)
			}
			actual := append([]byte(nil), buffer.Bytes()...)
			buffer.Release()
			if !bytes.Equal(actual, expected) || from.Port != uint16(sender.LocalAddr().(*net.UDPAddr).Port) {
				t.Fatalf("packet %d: length=%d/%d or source changed", i, len(actual), len(expected))
			}
		}
		receiver.SetReadDeadline(time.Now().Add(20 * time.Millisecond))
		if _, _, err := waiter.WaitReadPacket(); !errors.Is(err, os.ErrDeadlineExceeded) {
			t.Fatalf("deadline error=%v", err)
		}
	})
}

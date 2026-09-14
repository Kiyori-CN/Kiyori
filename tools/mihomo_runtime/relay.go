// Derived from MetaCubeX/mihomo v1.19.30 (GPL-3.0-or-later).
// Kiyori: record transport termination without payload, hosts, or credentials.
package net

import (
	"errors"
	"io"
	"log"
	"net"
	"os"
	"syscall"

	"github.com/metacubex/mihomo/common/net/deadline"

	"github.com/metacubex/sing/common"
	"github.com/metacubex/sing/common/bufio"
	"github.com/metacubex/sing/common/network"
)

var NewExtendedConn = bufio.NewExtendedConn
var NewExtendedWriter = bufio.NewExtendedWriter
var NewExtendedReader = bufio.NewExtendedReader

type ExtendedConn = network.ExtendedConn
type ExtendedWriter = network.ExtendedWriter
type ExtendedReader = network.ExtendedReader

var WriteBuffer = bufio.WriteBuffer

type ReadWaitOptions = network.ReadWaitOptions

var NewReadWaitOptions = network.NewReadWaitOptions
var CalculateFrontHeadroom = network.CalculateFrontHeadroom
var CalculateRearHeadroom = network.CalculateRearHeadroom

type ReaderWithUpstream = network.ReaderWithUpstream
type WithUpstreamReader = network.WithUpstreamReader
type WriterWithUpstream = network.WriterWithUpstream
type WithUpstreamWriter = network.WithUpstreamWriter
type WithUpstream = common.WithUpstream

var UnwrapReader = network.UnwrapReader
var UnwrapWriter = network.UnwrapWriter

func NewDeadlineConn(conn net.Conn) ExtendedConn {
	if deadline.IsPipe(conn) || deadline.IsPipe(UnwrapReader(conn)) {
		return NewExtendedConn(conn) // pipe always have correctly deadline implement
	}
	if deadline.IsConn(conn) || deadline.IsConn(UnwrapReader(conn)) {
		return NewExtendedConn(conn) // was a *deadline.Conn
	}
	return deadline.NewConn(conn)
}

func NeedHandshake(conn any) bool {
	if earlyConn, isEarlyConn := common.Cast[network.EarlyConn](conn); isEarlyConn && earlyConn.NeedHandshake() {
		return true
	}
	return false
}

type CountFunc = network.CountFunc

var Pipe = deadline.Pipe

func closeWrite(writer io.Closer) error {
	if c, ok := common.Cast[network.WriteCloser](writer); ok {
		return c.CloseWrite()
	}
	return writer.Close()
}

// Relay copies between left and right bidirectionally.
// like [bufio.CopyConn] but remove unneeded [context.Context] handle and the cost of [task.Group]
func Relay(leftConn, rightConn net.Conn) {
	defer func() {
		_ = leftConn.Close()
		_ = rightConn.Close()
	}()

	ch := make(chan struct{})
	go func() {
		_, err := bufio.Copy(leftConn, rightConn)
		logRelayResult(leftConn, "download", err)
		if err == nil {
			_ = closeWrite(leftConn)
		} else {
			_ = leftConn.Close()
		}
		close(ch)
	}()

	_, err := bufio.Copy(rightConn, leftConn)
	logRelayResult(leftConn, "upload", err)
	if err == nil {
		_ = closeWrite(rightConn)
	} else {
		_ = rightConn.Close()
	}
	<-ch
}

// Only controlled classifications and the local client's port leave the native boundary.
// A healthy controller cannot explain why one copy stopped; preserve this separate evidence.
func logRelayResult(client net.Conn, direction string, err error) {
	port := 0
	if address, ok := client.RemoteAddr().(*net.TCPAddr); ok {
		port = address.Port
	}
	result := "other_error"
	switch {
	case err == nil:
		result = "copy_complete"
	case errors.Is(err, io.ErrUnexpectedEOF):
		result = "unexpected_eof"
	case errors.Is(err, syscall.EINTR):
		result = "interrupted"
	case errors.Is(err, syscall.ECONNRESET):
		result = "connection_reset"
	case errors.Is(err, syscall.EPIPE):
		result = "broken_pipe"
	case errors.Is(err, os.ErrDeadlineExceeded):
		result = "deadline"
	case errors.Is(err, net.ErrClosed), errors.Is(err, io.ErrClosedPipe):
		result = "closed"
	}
	log.Printf("[Kiyori relay] direction=%s clientPort=%d result=%s", direction, port, result)
}

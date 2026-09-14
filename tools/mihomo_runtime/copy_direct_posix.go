//go:build !windows

// Derived from MetaCubeX/sing v0.5.7 (GPL-3.0-or-later).
// Kiyori: EINTR must retry the same unread TCP bytes or UDP datagram.
package bufio

import (
	"io"
	"log"
	"net/netip"
	"os"
	"syscall"

	"github.com/metacubex/sing/common/buf"
	E "github.com/metacubex/sing/common/exceptions"
	M "github.com/metacubex/sing/common/metadata"
	N "github.com/metacubex/sing/common/network"
)

var _ N.ReadWaiter = (*syscallReadWaiter)(nil)

type syscallReadWaiter struct {
	rawConn     syscall.RawConn
	readErr     error
	readFunc    func(fd uintptr) (done bool)
	buffer      *buf.Buffer
	options     N.ReadWaitOptions
	interrupted bool
}

func createSyscallReadWaiter(reader any) (*syscallReadWaiter, bool) {
	if syscallConn, isSyscallConn := reader.(syscall.Conn); isSyscallConn {
		rawConn, err := syscallConn.SyscallConn()
		if err == nil {
			return &syscallReadWaiter{rawConn: rawConn}, true
		}
	}
	return nil, false
}

func (w *syscallReadWaiter) InitializeReadWaiter(options N.ReadWaitOptions) (needCopy bool) {
	w.options = options
	w.readFunc = func(fd uintptr) (done bool) {
		buffer := w.options.NewBuffer()
		var readN int
		for {
			readN, w.readErr = syscall.Read(int(fd), buffer.FreeBytes())
			// EINTR consumes no bytes. Waiting for another poll edge here can stall a ready socket.
			if w.readErr != syscall.EINTR {
				break
			}
			if !w.interrupted {
				log.Print("[Kiyori raw read] recovered EINTR direction=readwait_tcp")
				w.interrupted = true
			}
		}
		if readN > 0 {
			buffer.Truncate(readN)
			w.options.PostReturn(buffer)
			w.buffer = buffer
		} else {
			buffer.Release()
		}
		//goland:noinspection GoDirectComparisonOfErrors
		if w.readErr == syscall.EAGAIN {
			return false
		}
		if readN == 0 && w.readErr == nil {
			w.readErr = io.EOF
		}
		return true
	}
	return false
}

func (w *syscallReadWaiter) WaitReadBuffer() (buffer *buf.Buffer, err error) {
	if w.readFunc == nil {
		return nil, os.ErrInvalid
	}
	err = w.rawConn.Read(w.readFunc)
	if err != nil {
		return
	}
	if w.readErr != nil {
		if w.readErr == io.EOF {
			return nil, io.EOF
		}
		return nil, E.Cause(w.readErr, "raw read")
	}
	buffer = w.buffer
	w.buffer = nil
	return
}

var _ N.PacketReadWaiter = (*syscallPacketReadWaiter)(nil)

type syscallPacketReadWaiter struct {
	rawConn     syscall.RawConn
	readErr     error
	readFrom    M.Socksaddr
	readFunc    func(fd uintptr) (done bool)
	buffer      *buf.Buffer
	options     N.ReadWaitOptions
	interrupted bool
}

func createSyscallPacketReadWaiter(reader any) (*syscallPacketReadWaiter, bool) {
	if syscallConn, isSyscallConn := reader.(syscall.Conn); isSyscallConn {
		rawConn, err := syscallConn.SyscallConn()
		if err == nil {
			return &syscallPacketReadWaiter{rawConn: rawConn}, true
		}
	}
	return nil, false
}

func (w *syscallPacketReadWaiter) InitializeReadWaiter(options N.ReadWaitOptions) (needCopy bool) {
	w.options = options
	w.readFunc = func(fd uintptr) (done bool) {
		buffer := w.options.NewPacketBuffer()
		var readN int
		var from syscall.Sockaddr
		for {
			readN, _, _, from, w.readErr = syscall.Recvmsg(int(fd), buffer.FreeBytes(), nil, 0)
			// Retry this receive only: never resend or discard the datagram on EINTR.
			if w.readErr != syscall.EINTR {
				break
			}
			if !w.interrupted {
				log.Print("[Kiyori raw read] recovered EINTR direction=readwait_udp")
				w.interrupted = true
			}
		}
		//goland:noinspection GoDirectComparisonOfErrors
		if w.readErr != nil {
			buffer.Release()
			return w.readErr != syscall.EAGAIN
		}
		if readN > 0 {
			buffer.Truncate(readN)
		}
		w.options.PostReturn(buffer)
		w.buffer = buffer
		switch fromAddr := from.(type) {
		case *syscall.SockaddrInet4:
			w.readFrom = M.SocksaddrFrom(netip.AddrFrom4(fromAddr.Addr), uint16(fromAddr.Port))
		case *syscall.SockaddrInet6:
			w.readFrom = M.SocksaddrFrom(netip.AddrFrom16(fromAddr.Addr), uint16(fromAddr.Port)).Unwrap()
		}
		return true
	}
	return false
}

func (w *syscallPacketReadWaiter) WaitReadPacket() (buffer *buf.Buffer, destination M.Socksaddr, err error) {
	if w.readFunc == nil {
		return nil, M.Socksaddr{}, os.ErrInvalid
	}
	err = w.rawConn.Read(w.readFunc)
	if err != nil {
		return
	}
	if w.readErr != nil {
		err = E.Cause(w.readErr, "raw read")
		return
	}
	buffer = w.buffer
	w.buffer = nil
	destination = w.readFrom
	return
}

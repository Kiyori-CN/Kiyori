const os = require("os");
const net = require("net");

// 系统 HTTP 代理不会改变服务监听；TUN 与虚拟交换机却会进入网卡列表，不能自动作为手机 LAN 地址。
function getNetworkSnapshot(interfaces = os.networkInterfaces()) {
  const candidates = [];
  for (const [interfaceName, entries] of Object.entries(interfaces)) {
    for (const item of entries || []) {
      if (!item || !["IPv4", 4].includes(item.family) || item.internal || net.isIP(item.address) !== 4) continue;
      const address = item.address;
      if (/^(?:127\.|169\.254\.|0\.)/.test(address)) continue;
      const isProxy = /mihomo|clash|meta.?tunnel|wintun|sing.?box|tun(?:nel)?\b/i.test(interfaceName)
        || /^198\.(?:18|19)\./.test(address);
      const isVirtual = isProxy || /vethernet|wsl|hyper-v|vmware|virtualbox|docker|container|tailscale|zerotier|loopback|npcap|hamachi|wireguard|vpn|虚拟/i.test(interfaceName);
      const isPrivateLan = /^(?:10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.)/.test(address);
      const score = (isPrivateLan ? 100 : 0) + (isVirtual ? -200 : 70)
        + (/wi-?fi|wlan|wireless|ethernet|以太网|无线/i.test(interfaceName) ? 25 : 0);
      candidates.push({ address, interfaceName, isPrivateLan, isVirtual, isProxy, score });
    }
  }
  candidates.sort((a, b) => b.score - a.score || a.address.localeCompare(b.address));
  const rankedIpv4Candidates = candidates.filter((item, index) => candidates.findIndex(other => other.address === item.address) === index);
  const recommended = rankedIpv4Candidates.find(item => !item.isVirtual);
  return {
    ipv4Candidates: rankedIpv4Candidates.map(item => item.address),
    preferredLan: rankedIpv4Candidates.find(item => item.isPrivateLan && !item.isVirtual)?.address || "",
    recommendedHost: recommended?.address || "",
    recommendedInterfaceName: recommended?.interfaceName || "",
    proxyInterfaceDetected: rankedIpv4Candidates.some(item => item.isProxy),
    rankedIpv4Candidates
  };
}

function validateBindAddress(address, interfaces = os.networkInterfaces()) {
  if (typeof address !== "string" || !address.trim()) throw new Error("BIND_ADDRESS_REQUIRED");
  if (["0.0.0.0", "::", "127.0.0.1", "::1", "localhost"].includes(address)) return;
  if (!net.isIP(address)) throw new Error("BIND_ADDRESS_INVALID: use a local IP address");
  if (!Object.values(interfaces).flat().some(item => item?.address === address)) {
    throw new Error("BIND_ADDRESS_UNAVAILABLE: select an address from the current network adapters");
  }
}

module.exports = { getNetworkSnapshot, validateBindAddress };

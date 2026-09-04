import { buildQuery, signWbi } from "./crypto";
import { parseJson, recordAt, requireRecord, stringAt } from "./json";
import type { JsonRecord, QueryParams } from "./types";
import { BilibiliError, failureDetails } from "./errors";

const API_BASE = "https://api.bilibili.com";

export class BilibiliClient {
  private wbiKeys: { img: string; sub: string } | null = null;
  private cookieState = false;
  private blockedError: BilibiliError | null = null;
  private keysLoadedAt = 0;

  get cookieConfigured(): boolean {
    return this.cookieState;
  }

  async api(
    path: string,
    params: QueryParams = {},
    signed = false,
    anonymous = false
  ): Promise<JsonRecord> {
    let query: string;
    if (signed) {
      const keys = await this.getWbiKeys();
      query = signWbi(params, keys.img, keys.sub, Math.floor(Date.now() / 1000));
    } else {
      query = buildQuery(params);
    }
    const url = API_BASE + path + (query.length > 0 ? "?" + query : "");
    const response = await this.get({
      mode: anonymous ? "api_anonymous" : "api",
      url
    });
    this.cookieState = response.cookie_configured;
    return requireRecord(parseJson(response.body, "Bilibili API"), "Bilibili API response");
  }

  async publicJson(url: string): Promise<JsonRecord> {
    const response = await this.get({ mode: "public", url });
    return requireRecord(parseJson(response.body, "Bilibili public resource"), "Bilibili resource");
  }

  async publicText(url: string): Promise<string> {
    const response = await this.get({ mode: "public", url });
    return response.body;
  }

  async publicBinary(url: string): Promise<string> {
    const response = await this.get({ mode: "public", url });
    if (response.body_encoding !== "base64") {
      throw new Error("弹幕二进制响应缺少 base64 编码标识；请更新宿主。 ");
    }
    return response.body;
  }

  async resolveShortLink(url: string): Promise<string> {
    const response = await this.get({ mode: "resolve", url });
    return response.final_url;
  }

  async nav(anonymous = false): Promise<JsonRecord> {
    return this.api("/x/web-interface/nav", {}, false, anonymous);
  }

  private async get(request: ToolPkg.BilibiliHostRequest): Promise<ToolPkg.BilibiliHostResponse> {
    if (this.blockedError !== null) throw this.blockedError;
    try {
      return await ToolPkg.services.bilibili.get(request);
    } catch (error) {
      const detail = failureDetails(error);
      console.error("Bilibili 请求失败：" + JSON.stringify(detail));
      if (detail.code === "RISK_CONTROL" || detail.code === "CANCELLED") {
        this.blockedError = new BilibiliError(detail.code, typeof detail.message === "string" ? detail.message : "请求已停止。", detail);
      }
      throw error;
    }
  }

  private async getWbiKeys(): Promise<{ img: string; sub: string }> {
    if (this.wbiKeys !== null && Date.now() - this.keysLoadedAt < 300_000) {
      return this.wbiKeys;
    }
    const payload = await this.nav(true);
    const data = recordAt(payload, "data");
    const wbi = data === null ? null : recordAt(data, "wbi_img");
    const imgUrl = wbi === null ? null : stringAt(wbi, "img_url");
    const subUrl = wbi === null ? null : stringAt(wbi, "sub_url");
    if (imgUrl === null || subUrl === null) {
      throw new Error("Bilibili nav response did not provide WBI image keys.");
    }
    this.wbiKeys = {
      img: extractFileStem(imgUrl, "img_url"),
      sub: extractFileStem(subUrl, "sub_url")
    };
    this.keysLoadedAt = Date.now();
    return this.wbiKeys;
  }
}

function extractFileStem(url: string, label: string): string {
  const withoutQuery = url.split("?", 1)[0];
  const match = withoutQuery.match(/\/([^/]+)\.[A-Za-z0-9]+$/);
  if (match === null || match[1].length === 0) {
    throw new Error("Bilibili WBI " + label + " is invalid.");
  }
  return match[1];
}

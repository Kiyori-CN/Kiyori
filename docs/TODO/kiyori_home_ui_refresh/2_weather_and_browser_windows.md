# 2. 天气与浏览器窗口

状态：[DONE]

## 天气

- 已授权定位时首页自动刷新；未授权时只显示天气入口，点击后请求权限。
- 定位使用 Android/AndroidX 原生位置 API；城市使用 `Geocoder` 解析。
- 当前天气请求使用 Open-Meteo `current=temperature_2m,weather_code&timezone=auto`，默认摄氏度。
- 成功状态显示 WMO code 对应图标、整数温度与当前城市。
- 点击成功天气，以当前搜索引擎搜索“城市名 天气”并进入 Browser Home。
- 定位、城市解析或网络失败时显示明确的可重试状态，不展示旧城市或虚假温度。
- Open-Meteo 免费接口限非商业用途；坐标会发送给第三方服务，文档必须保留该隐私与许可边界。

## 窗口

- 首页显示共享 Browser Runtime 的真实窗口总数。
- 首页与浏览器底栏第四项共同使用 `WebSessionBrowserWindowCountIcon` 的方框数字视觉。
- 点击按钮打开 Browser Home，并令原 `WebSessionBrowserHost` 显示 `WebSessionBrowserSheetRoute.TABS`。
- 不创建首页专用标签列表、页面副本或第二个 WebView。

## 验收

- 创建、关闭浏览器窗口后首页计数同步。
- 首页窗口按钮和浏览器底栏第四项到达同一个窗口总览页面。
- 天气权限拒绝、定位失败和请求失败不会触发无关导航。

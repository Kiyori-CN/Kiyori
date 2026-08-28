/* METADATA
{
  name: time

  display_name: {
    zh: "时间"
    en: "Time"
  }
  description: {
    zh: "获取当前本地时间、日期、星期和时间戳，或返回标准 12/24 小时格式。"
    en: "Return the current local date, weekday, timestamp, and structured time, or standard 12-hour and 24-hour formats."
  }
  enabledByDefault: true
  category: "Utility"
  tools: [
    {
      name: get_time
      description: {
        zh: "返回当前时间戳、ISO 时间、本地日期时间及拆分后的日期和时间字段。"
        en: "Return the current timestamp, ISO and local date-time strings, plus structured date and time fields."
      }
      parameters: []
    },
    {
      name: format_time
      description: {
        zh: "返回当前日期、ISO 时间以及 12 小时制和 24 小时制时间字符串。"
        en: "Return the current date, ISO time, and 12-hour and 24-hour time strings."
      }
      parameters: []
    }
  ]
}*/

const timePackage = (function () {
  async function get_time(): Promise<any> {
    const now = new Date();

    return {
      timestamp: now.getTime(),
      iso: now.toISOString(),
      local: now.toLocaleString(),
      date: {
        year: now.getFullYear(),
        month: now.getMonth() + 1,
        day: now.getDate(),
        weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
      },
      time: {
        hours: now.getHours(),
        minutes: now.getMinutes(),
        seconds: now.getSeconds()
      }
    };
  }

  async function format_time(): Promise<any> {
    const now = new Date();

    const pad = (n: number) => n.toString().padStart(2, '0');

    const hours = now.getHours();
    const minutes = now.getMinutes();
    const seconds = now.getSeconds();

    const time24h = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;

    const suffix = hours >= 12 ? 'PM' : 'AM';
    const hour12 = hours % 12 === 0 ? 12 : hours % 12;
    const time12h = `${pad(hour12)}:${pad(minutes)}:${pad(seconds)} ${suffix}`;

    return {
      iso: now.toISOString(),
      date: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
      time24h,
      time12h
    };
  }

  return {
    get_time,
    format_time
  };
})();

// 逐个导出
exports.get_time = timePackage.get_time;
exports.format_time = timePackage.format_time;

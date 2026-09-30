/* InterviewGuide 项目拆解 · 站点脚本：主题切换、移动端导航、本页目录、问答自测、复制提示词 */
(function () {
  "use strict";

  var root = document.documentElement;
  var THEME_KEY = "ig-analysis-theme";

  function readTheme() {
    try {
      return window.localStorage.getItem(THEME_KEY);
    } catch (e) {
      return null;
    }
  }

  function writeTheme(value) {
    try {
      window.localStorage.setItem(THEME_KEY, value);
    } catch (e) {
      /* 存储不可用时只在本次浏览生效 */
    }
  }

  function effectiveTheme() {
    var explicit = root.getAttribute("data-theme");
    if (explicit === "dark" || explicit === "light") {
      return explicit;
    }
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  var themeBtn = document.querySelector("[data-theme-toggle]");

  function syncThemeButton() {
    if (!themeBtn) {
      return;
    }
    var dark = effectiveTheme() === "dark";
    themeBtn.textContent = dark ? "浅色" : "深色";
    themeBtn.setAttribute("aria-label", dark ? "切换到浅色主题" : "切换到深色主题");
  }

  if (themeBtn) {
    themeBtn.addEventListener("click", function () {
      var next = effectiveTheme() === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      writeTheme(next);
      syncThemeButton();
    });
    syncThemeButton();
  }

  /* 移动端导航 */
  var navBtn = document.querySelector("[data-nav-toggle]");
  var nav = document.getElementById("sitenav");
  if (navBtn && nav) {
    navBtn.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      navBtn.setAttribute("aria-expanded", open ? "true" : "false");
      navBtn.textContent = open ? "关闭" : "页面";
    });
  }

  /* 本页目录：由正文 h2/h3 自动生成 */
  var main = document.getElementById("main");
  var tocList = document.getElementById("toc-list");
  var tocBox = document.querySelector(".toc-box");
  var links = [];
  if (main && tocList) {
    var headings = main.querySelectorAll("h2[id], h3[id]");
    headings.forEach(function (h) {
      var li = document.createElement("li");
      if (h.tagName === "H3") {
        li.className = "sub";
      }
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.textContent = h.getAttribute("data-toc") || h.textContent;
      li.appendChild(a);
      tocList.appendChild(li);
      links.push({ el: h, link: a });
    });
    if (links.length === 0 && tocBox) {
      tocBox.hidden = true;
    }
  }

  if (tocBox && window.matchMedia && window.matchMedia("(min-width: 1100px)").matches) {
    tocBox.open = true;
  }

  if (links.length && "IntersectionObserver" in window) {
    var current = null;
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            var found = links.filter(function (item) {
              return item.el === entry.target;
            })[0];
            if (found && found !== current) {
              if (current) {
                current.link.classList.remove("active");
              }
              found.link.classList.add("active");
              current = found;
            }
          }
        });
      },
      { rootMargin: "-80px 0px -70% 0px", threshold: 0 }
    );
    links.forEach(function (item) {
      observer.observe(item.el);
    });
  }

  /* 问答页：自测模式（收起全部答案）与全部展开 */
  document.querySelectorAll("[data-qa-action]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var open = btn.getAttribute("data-qa-action") === "open";
      document.querySelectorAll("details.qa").forEach(function (d) {
        d.open = open;
      });
    });
  });

  /* 复制提示词 */
  document.querySelectorAll("[data-copy]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var target = document.getElementById(btn.getAttribute("data-copy"));
      if (!target) {
        return;
      }
      var text = target.innerText;
      var done = function () {
        var old = btn.textContent;
        btn.textContent = "已复制";
        window.setTimeout(function () {
          btn.textContent = old;
        }, 1600);
      };
      var fallback = function () {
        var range = document.createRange();
        range.selectNodeContents(target);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        btn.textContent = "已选中，按 Ctrl/⌘+C 复制";
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, fallback);
      } else {
        fallback();
      }
    });
  });
})();

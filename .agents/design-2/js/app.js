/* ============================================================================
   MediaNest — Application core
   Shared runtime + contract for every screen module.

   >>> CONTRACT FOR SCREEN MODULES <<<
   * Build HTML with SINGLE-QUOTE concatenation ('<div>' + x + '</div>').
     NEVER start a template literal with '<' (the formatter misparses it as JSX
     and corrupts the file).
   * NEVER use .innerHTML / .outerHTML / .insertAdjacentHTML (blocking lint).
     Inject HTML via MN.render(el, htmlString); inject text via textContent.
   * Icons: MN.icon('home') returns an <svg><use> string. Include via render().
   * Escape any user-ish data with MN.esc(...).
   * Register a screen: MN.router.register('name', { title, back, mount(el,params), unmount }).
     mount() returns an optional instance with an unmount() hook.
   * Navigate: MN.router.navigate('name'); MN.router.back().
   * Re-render on state change: MN.store.subscribe(fn); call MN.render(el, html).
   ========================================================================== */

(() => {
	var DATA = window.MN_DATA;

	/* ==========================================================================
     0. Small utilities
     ======================================================================== */

	function esc(s) {
		return String(s == null ? "" : s)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#39;");
	}

	/* icon(name, cls) -> SVG string via the sprite */
	function icon(name, cls) {
		var c = cls ? " " + cls : "";
		return (
			'<svg class="mn-icon' +
			c +
			'" aria-hidden="true"><use href="#i-' +
			name +
			'"></use></svg>'
		);
	}

	/* h(tag, cls, html) -> element HTML string */
	function h(tag, cls, html) {
		var c = cls ? ' class="' + cls + '"' : "";
		return "<" + tag + c + ">" + (html == null ? "" : html) + "</" + tag + ">";
	}

	function qs(sel, root) {
		return (root || document).querySelector(sel);
	}

	/* Parse a trusted HTML string into a DocumentFragment using DOMParser.
     (Deliberately avoids innerHTML/outerHTML/insertAdjacentHTML.) */
	function toFragment(html) {
		var doc = new DOMParser().parseFromString(
			'<div class="mn-tpl">' + html + "</div>",
			"text/html",
		);
		var wrap = doc.body.firstElementChild;
		var frag = document.createDocumentFragment();
		while (wrap && wrap.firstChild) {
			frag.append(wrap.firstChild);
		}
		return frag;
	}

	function render(el, html) {
		el.replaceChildren(toFragment(html));
	}

	function domFrom(html) {
		return toFragment(html).firstElementChild;
	}

	/* ==========================================================================
     1. Formatting helpers
     ======================================================================== */

	function pad2(n) {
		return n < 10 ? "0" + n : "" + n;
	}

	function fmtDuration(sec) {
		sec = Math.max(0, Math.round(sec || 0));
		var hh = Math.floor(sec / 3600);
		var mm = Math.floor((sec % 3600) / 60);
		var ss = sec % 60;
		if (hh > 0) return hh + ":" + pad2(mm) + ":" + pad2(ss);
		return mm + ":" + pad2(ss);
	}

	function fmtBytes(b) {
		b = Math.max(0, b || 0);
		if (b < 1024) return b + " B";
		var units = ["KB", "MB", "GB", "TB"];
		var i = -1;
		do {
			b /= 1024;
			i++;
		} while (b >= 1024 && i < units.length - 1);
		return b.toFixed(b >= 100 ? 0 : 1) + " " + units[i];
	}

	function fmtSpeed(bps) {
		if (!bps || bps <= 0) return "";
		return fmtBytes(bps) + "/s";
	}

	function relTime(ts) {
		var diff = Math.max(0, Date.now() - ts);
		var min = Math.floor(diff / 60000);
		if (min < 1) return "just now";
		if (min < 60) return min + "m ago";
		var hr = Math.floor(min / 60);
		if (hr < 24) return hr + "h ago";
		var day = Math.floor(hr / 24);
		if (day < 7) return day + "d ago";
		if (day < 30) return Math.floor(day / 7) + "w ago";
		if (day < 365) return Math.floor(day / 30) + "mo ago";
		return Math.floor(day / 365) + "y ago";
	}

	/* ==========================================================================
     2. Store (tiny pub/sub)
     ======================================================================== */

	function Store(initial) {
		this.state = initial || {};
		this.subs = [];
	}
	Store.prototype.get = function () {
		return this.state;
	};
	Store.prototype.set = function (patch) {
		var next = Object.assign({}, this.state, patch);
		this.state = next;
		this.subs.forEach((fn) => {
			fn(next, this.state);
		});
	};
	Store.prototype.update = function (fn) {
		var next = fn(this.state);
		if (next) this.set(next);
	};
	Store.prototype.subscribe = function (fn) {
		this.subs.push(fn);
	};

	var store = new Store({
		playing: null,
		queue: [],
		queueIndex: -1,
		isPlaying: false,
		positionMs: 0,
		durationMs: 0,
		bufferedMs: 0,
		autoplay: true,
		speed: 1,
		quality: "1080p",
		isLocal: false,
		maxConcurrent: 2,
		notifCount: DATA.notifications.length,
	});

	/* ==========================================================================
     3. Router
     ======================================================================== */

	var routes = {};
	var currentRoute = null;
	var currentParams = {};
	var currentScreen = null;
	var historyStack = [];

	function parseHash() {
		var raw = location.hash.replace(/^#\/?/, "") || "home";
		return raw.split("?")[0];
	}

	function parseParams() {
		var raw = location.hash.replace(/^#\/?/, "");
		var q = raw.split("?")[1];
		var out = {};
		if (q) {
			q.split("&").forEach((pair) => {
				var kv = pair.split("=");
				out[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || "");
			});
		}
		return out;
	}

	function routeKey(path) {
		var segs = path.split("/");
		var keys = Object.keys(routes);
		for (var i = 0; i < keys.length; i++) {
			var rsegs = keys[i].split("/");
			if (rsegs.length !== segs.length) continue;
			var ok = true;
			var params = {};
			for (var j = 0; j < rsegs.length; j++) {
				if (rsegs[j].charAt(0) === ":") {
					params[rsegs[j].slice(1)] = segs[j];
				} else if (rsegs[j] !== segs[j]) {
					ok = false;
					break;
				}
			}
			if (ok) return { key: keys[i], params: params };
		}
		return null;
	}

	function paramsToQuery(p) {
		var parts = [];
		Object.keys(p || {}).forEach((k) => {
			parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(p[k]));
		});
		return parts.length ? "?" + parts.join("&") : "";
	}

	function go(path, pushHistory) {
		if (pushHistory && currentRoute) {
			historyStack.push({ route: currentRoute, params: currentParams });
		}
		location.hash = "#/" + path;
	}

	function navigate(path) {
		go(path, true);
	}

	function back() {
		if (historyStack.length) {
			var prev = historyStack.pop();
			location.hash = "#/" + prev.route + paramsToQuery(prev.params);
		} else {
			location.hash = "#/home";
		}
	}

	function registerRoute(key, def) {
		routes[key] = def;
	}

	function mountScreen(key, params) {
		var container = qs("#view-root");
		var def = routes[key];
		if (!def) return;
		if (currentScreen && typeof currentScreen.unmount === "function") {
			try {
				currentScreen.unmount();
			} catch (e) {
				console.error(e);
			}
		}
		currentRoute = key;
		currentParams = params || {};
		container.replaceChildren();
		container.scrollTop = 0;
		var inst = def.mount(container, currentParams) || {};
		currentScreen = inst;
		updateAppbar(def, currentParams);
		updateNav(key);
		updateMiniPlayerVisibility();
	}

	function updateAppbar(def, params) {
		var bar = qs("#appbar");
		var titleEl = qs("#appbar-title");
		var backEl = qs("#appbar-back");
		var actionsEl = qs("#appbar-actions");
		if (!bar) return;
		if (def && def.title) titleEl.textContent = def.title;
		if (def && def.appbar === false) {
			bar.classList.add("mn-hidden");
		} else {
			bar.classList.remove("mn-hidden");
		}
		backEl.classList.toggle("mn-hidden", !(def && def.back));
		if (def && def.actions) {
			render(actionsEl, def.actions(params));
		} else {
			actionsEl.replaceChildren();
		}
	}

	function updateNav(key) {
		document.querySelectorAll(".mn-nav__item").forEach((it) => {
			it.classList.toggle(
				"mn-nav__item--active",
				it.getAttribute("data-route") === key,
			);
		});
	}

	function updateMiniPlayerVisibility() {
		var mp = qs("#miniplayer");
		if (!mp) return;
		var st = store.get();
		var onPlayer = currentRoute === "player" || currentRoute === "player-offline";
		var show = !!st.playing && !onPlayer;
		mp.classList.toggle("mn-hidden", !show);
	}

	window.addEventListener("hashchange", () => {
		var r = routeKey(parseHash()) || { key: "home", params: {} };
		mountScreen(r.key, r.params);
	});

	/* ==========================================================================
     4. Overlays: toast, sheet, dialog
     ======================================================================== */

	function toast(msg, type) {
		var host = qs("#toast-host");
		var t = document.createElement("div");
		var iconName = "info";
		if (type === "success") iconName = "check-circle";
		if (type === "error") iconName = "warning";
		t.className = "mn-toast mn-toast--" + (type || "info");
		render(
			t,
			'<span class="mn-toast__dot">' +
				icon(iconName) +
				'</span><span class="mn-toast__text">' +
				esc(msg) +
				"</span>",
		);
		host.append(t);
		setTimeout(() => {
			t.classList.add("mn-toast--leaving");
			setTimeout(() => {
				if (t.parentNode) t.parentNode.removeChild(t);
			}, 200);
		}, 3200);
	}

	function sheet(opts) {
		var scrim = qs("#sheet-scrim");
		var sheetEl = qs("#sheet");
		var bodyEl = qs("#sheet-body");
		var titleEl = qs("#sheet-title");
		var closeBtn = qs("#sheet-close");
		titleEl.textContent = opts.title || "";
		render(bodyEl, opts.body || "");
		render(closeBtn, icon("close"));
		closeBtn.onclick = closeSheet;
		sheetEl.classList.add("mn-sheet--open");
		scrim.classList.add("mn-sheet-scrim--open");
		scrim.onclick = closeSheet;
		if (opts.onOpen) opts.onOpen(bodyEl);
	}

	function closeSheet() {
		var scrim = qs("#sheet-scrim");
		var sheetEl = qs("#sheet");
		sheetEl.classList.remove("mn-sheet--open");
		scrim.classList.remove("mn-sheet-scrim--open");
		scrim.onclick = null;
	}

	function dialog(opts) {
		var scrim = qs("#dialog-scrim");
		var dlg = qs("#dialog");
		dlg.replaceChildren();
		if (opts.title) {
			dlg.append(
				domFrom('<h3 class="mn-dialog__title">' + esc(opts.title) + "</h3>"),
			);
		}
		if (opts.body) {
			dlg.append(domFrom('<div class="mn-dialog__body">' + opts.body + "</div>"));
		}
		if (opts.actions && opts.actions.length) {
			var wrap = document.createElement("div");
			wrap.className = "mn-dialog__actions";
			opts.actions.forEach((a) => {
				var btn = document.createElement("button");
				btn.className = "mn-btn mn-btn--sm " + (a.cls || "mn-btn--ghost");
				btn.textContent = a.label;
				btn.onclick = () => {
					closeDialog();
					if (a.onClick) a.onClick();
				};
				wrap.append(btn);
			});
			dlg.append(wrap);
		}
		scrim.classList.add("mn-dialog-scrim--open");
		scrim.onclick = (e) => {
			if (e.target === scrim && opts.dismissible !== false) closeDialog();
		};
	}

	function closeDialog() {
		var scrim = qs("#dialog-scrim");
		scrim.classList.remove("mn-dialog-scrim--open");
		scrim.onclick = null;
	}

	/* ==========================================================================
     5. Playback simulation
     ======================================================================== */

	var tickerId = null;

	function ensureTicker() {
		if (tickerId) return;
		tickerId = setInterval(() => {
			var st = store.get();
			if (st.isPlaying && st.playing && st.durationMs > 0) {
				var next = st.positionMs + 1000 * st.speed;
				if (next >= st.durationMs) {
					onTrackEnded();
				} else {
					store.set({
						positionMs: next,
						bufferedMs: Math.min(st.durationMs, st.positionMs + 60000),
					});
					renderMiniPlayer();
				}
			}
		}, 1000);
	}

	function onTrackEnded() {
		var st = store.get();
		if (st.autoplay && st.queue.length > 0) {
			playIndex(st.queueIndex + 1);
		} else {
			store.set({ isPlaying: false });
			renderMiniPlayer();
		}
	}

	function playIndex(index) {
		var st = store.get();
		if (index < 0 || index >= st.queue.length) return;
		var track = st.queue[index];
		store.set({
			playing: track,
			queueIndex: index,
			positionMs: 0,
			durationMs: (track.durationSeconds || 0) * 1000,
			bufferedMs: 30000,
			isPlaying: true,
			isLocal: !!track.isLocal,
			quality: track.resolution || st.quality,
		});
		ensureTicker();
		renderMiniPlayer();
	}

	function play(queue, startIndex) {
		startIndex = startIndex || 0;
		store.set({ queue: queue || [], queueIndex: startIndex });
		playIndex(startIndex);
	}

	function togglePlay() {
		var st = store.get();
		if (!st.playing) return;
		store.set({ isPlaying: !st.isPlaying });
		ensureTicker();
		renderMiniPlayer();
	}

	function next() {
		var st = store.get();
		if (st.queue.length === 0) return;
		var idx = (st.queueIndex + 1) % st.queue.length;
		playIndex(idx);
	}

	function prev() {
		var st = store.get();
		if (st.queue.length === 0) return;
		if (st.positionMs > 5000) {
			store.set({ positionMs: 0 });
			renderMiniPlayer();
			return;
		}
		var idx = (st.queueIndex - 1 + st.queue.length) % st.queue.length;
		playIndex(idx);
	}

	function seek(ms) {
		var st = store.get();
		if (!st.playing) return;
		var clamped = Math.max(0, Math.min(st.durationMs, ms));
		store.set({ positionMs: clamped });
		renderMiniPlayer();
	}

	function seekRelative(delta) {
		seek(store.get().positionMs + delta);
	}

	function setAutoplay(v) {
		store.set({ autoplay: v });
	}
	function setSpeed(v) {
		store.set({ speed: v });
	}
	function setQuality(q) {
		store.set({ quality: q });
	}

	/* ==========================================================================
     6. Download simulation
     ======================================================================== */

	var dlTicker = null;
	var dlListeners = [];

	function emitDownloadTicks() {
		dlListeners.forEach((fn) => {
			try {
				fn();
			} catch (e) {
				console.error(e);
			}
		});
	}

	function ensureDlTicker() {
		if (dlTicker) return;
		dlTicker = setInterval(() => {
			var changed = false;
			DATA.downloads.forEach((dl) => {
				if (dl.status === "DOWNLOADING") {
					var inc = (dl.speedBytesPerSec || 2000000) * 0.5;
					dl.downloadedBytes = Math.min(dl.fileSizeBytes, dl.downloadedBytes + inc);
					dl.progress = dl.downloadedBytes / dl.fileSizeBytes;
					dl.elapsedMs += 500;
					if (dl.progress >= 1) {
						dl.status = "COMPLETED";
						dl.progress = 1;
						dl.remainingMs = 0;
						addNotification({
							type: "success",
							title: "Download complete",
							desc: esc(dl.title) + " is ready to play.",
							channel: "Downloads",
						});
						toast("Download complete", "success");
					} else {
						dl.remainingMs =
							Math.round(
								(dl.fileSizeBytes - dl.downloadedBytes) / dl.speedBytesPerSec,
							) * 1000;
					}
					changed = true;
				}
			});
			if (changed) {
				emitDownloadTicks();
				updateNotifBadge();
			}
		}, 500);
	}

	function enqueueDownload(video, format, quality) {
		ensureDlTicker();
		var dl = {
			id: DATA.downloads.length + 1,
			videoId: video.id,
			title: video.title,
			thumbnailUrl: video.thumbnailUrl,
			format: format || "video",
			quality:
				quality ||
				(format === "audio" ? "128kbps (opus)" : video.resolution || "720p"),
			status: "QUEUED",
			progress: 0,
			fileSizeBytes: 80000000 + (video.durationSeconds || 300) * 60000,
			downloadedBytes: 0,
			speedBytesPerSec: 0,
			elapsedMs: 0,
			remainingMs: 0,
			filePath: "",
			downloadedAt: Date.now(),
			errorMessage: null,
		};
		DATA.downloads.unshift(dl);
		setTimeout(() => {
			dl.status = "DOWNLOADING";
			dl.speedBytesPerSec = 3000000 + Math.random() * 3000000;
			emitDownloadTicks();
			updateNotifBadge();
		}, 900);
		toast("Added to download queue", "info");
		updateNotifBadge();
		return dl;
	}

	function pauseDownload(dl) {
		dl.status = "PAUSED";
		emitDownloadTicks();
	}
	function resumeDownload(dl) {
		dl.status = "DOWNLOADING";
		dl.speedBytesPerSec = 3000000 + Math.random() * 3000000;
		ensureDlTicker();
		emitDownloadTicks();
	}
	function retryDownload(dl) {
		dl.status = "DOWNLOADING";
		dl.speedBytesPerSec = 3000000;
		ensureDlTicker();
		emitDownloadTicks();
	}
	function cancelDownload(dl) {
		dl.status = "CANCELED";
		emitDownloadTicks();
	}
	function deleteDownload(dl) {
		var idx = DATA.downloads.indexOf(dl);
		if (idx >= 0) DATA.downloads.splice(idx, 1);
		emitDownloadTicks();
		updateNotifBadge();
	}

	/* ==========================================================================
     7. Notifications
     ======================================================================== */

	function addNotification(n) {
		DATA.notifications.unshift({
			id: Date.now(),
			type: n.type || "info",
			title: n.title,
			desc: n.desc || "",
			time: Date.now(),
			channel: n.channel || "MediaNest",
		});
		store.set({ notifCount: DATA.notifications.length });
		updateNotifBadge();
	}

	function updateNotifBadge() {
		var badge = qs("#notif-badge");
		if (!badge) return;
		var n = store.get().notifCount;
		badge.textContent = n > 9 ? "9+" : n;
		badge.classList.toggle("mn-hidden", n === 0);
	}

	/* ==========================================================================
     8. Mini player rendering
     ======================================================================== */

	function renderMiniPlayer() {
		var mp = qs("#miniplayer");
		if (!mp) return;
		var st = store.get();
		if (!st.playing) {
			mp.classList.add("mn-hidden");
			return;
		}
		mp.classList.remove("mn-hidden");
		var img = st.playing.thumbnailUrl || "";
		var progress = st.durationMs > 0 ? (st.positionMs / st.durationMs) * 100 : 0;
		render(
			mp,
			'<div class="mn-miniplayer__row">' +
				'<img class="mn-miniplayer__thumb" src="' +
				img +
				'" alt=""/>' +
				'<div class="mn-miniplayer__body">' +
				'<p class="mn-miniplayer__title">' +
				esc(st.playing.title) +
				"</p>" +
				'<p class="mn-miniplayer__meta">' +
				esc(st.playing.channelName || "") +
				"</p>" +
				"</div>" +
				'<button class="mn-icon-btn mn-miniplayer__toggle" aria-label="Play/Pause">' +
				icon(st.isPlaying ? "pause" : "play") +
				"</button>" +
				'<button class="mn-icon-btn mn-miniplayer__next" aria-label="Next">' +
				icon("next") +
				"</button>" +
				"</div>" +
				'<div class="mn-miniplayer__progress"><span style="width:' +
				progress +
				'%"></span></div>',
		);
		mp.onclick = (e) => {
			if (e.target.closest(".mn-miniplayer__toggle")) {
				togglePlay();
				return;
			}
			if (e.target.closest(".mn-miniplayer__next")) {
				next();
				return;
			}
			navigate(st.isLocal ? "player-offline" : "player");
		};
		updateMiniPlayerVisibility();
	}

	/* ==========================================================================
     9. Bootstrap
     ======================================================================== */

	function navItemsHtml() {
		var items = [
			{ route: "home", label: "Home", icon: "home" },
			{ route: "collections", label: "Collections", icon: "library" },
			{ route: "downloads", label: "Downloads", icon: "download" },
			{ route: "settings", label: "Settings", icon: "settings" },
		];
		var html = "";
		items.forEach((it) => {
			html +=
				'<button class="mn-nav__item" data-route="' +
				it.route +
				'">' +
				'<span class="mn-nav__item__indicator"></span>' +
				'<span style="position:relative">' +
				icon(it.icon) +
				"</span>" +
				'<span class="mn-nav__item__label">' +
				it.label +
				"</span>" +
				"</button>";
		});
		return html;
	}

	function appbarHtml() {
		return (
			"" +
			'<div id="appbar" class="mn-appbar">' +
			'<button id="appbar-back" class="mn-back mn-hidden" aria-label="Back">' +
			icon("back") +
			"</button>" +
			'<div class="mn-fill"><h1 id="appbar-title" class="mn-appbar__title">MediaNest</h1></div>' +
			'<div id="appbar-actions" class="mn-row mn-gap-2"></div>' +
			'<button id="notif-btn" class="mn-icon-btn" aria-label="Notifications">' +
			icon("bell") +
			'<span id="notif-badge" class="mn-appbar__badge mn-hidden"></span>' +
			"</button>" +
			"</div>"
		);
	}

	function bootstrap() {
		var app = qs("#app");
		render(
			app,
			appbarHtml() +
				'<main id="view-root" class="mn-view"></main>' +
				'<div id="miniplayer" class="mn-miniplayer mn-hidden"></div>' +
				'<nav class="mn-nav">' +
				navItemsHtml() +
				"</nav>" +
				'<div id="toast-host" class="mn-toast-host"></div>' +
				'<div id="sheet-scrim" class="mn-sheet-scrim"><div id="sheet" class="mn-sheet">' +
				'<div class="mn-sheet__handle"></div>' +
				'<div class="mn-sheet__header"><h3 id="sheet-title" class="mn-sheet__title"></h3>' +
				'<button id="sheet-close" class="mn-icon-btn mn-icon-btn--sm" aria-label="Close">' +
				icon("close") +
				"</button></div>" +
				'<div id="sheet-body" class="mn-sheet__body"></div>' +
				"</div></div>" +
				'<div id="dialog-scrim" class="mn-dialog-scrim"><div id="dialog" class="mn-dialog"></div></div>',
		);

		qs("#appbar-back").onclick = back;
		qs("#notif-btn").onclick = () => {
			navigate("notifications");
		};
		var appbarActions = qs("#appbar-actions");
		if (appbarActions) {
			appbarActions.onclick = (e) => {
				var viewToggle = e.target.closest("#view-toggle");
				if (viewToggle) {
					if (
						window.MN.collections &&
						typeof window.MN.collections.toggleView === "function"
					) {
						window.MN.collections.toggleView();
					}
					return;
				}
				var statsToggle = e.target.closest("#stats-toggle");
				if (statsToggle) {
					navigate("statistics");
					return;
				}
			};
		}
		qs(".mn-nav")
			.querySelectorAll(".mn-nav__item")
			.forEach((it) => {
				it.onclick = () => {
					navigate(it.getAttribute("data-route"));
				};
			});
		registerNotificationsScreen();
		updateNotifBadge();
		ensureDlTicker();
		mountScreen("home", {});
	}

	/* Inline Notifications screen (cross-cutting, small). */
	function registerNotificationsScreen() {
		registerRoute("notifications", {
			title: "Notifications",
			back: true,
			mount: (el) => {
				var visibleCount = 10;

				function onScroll() {
					var list = DATA.notifications;
					if (visibleCount >= list.length) return;
					if (el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
						visibleCount += 10;
						renderNotifs();
					}
				}

				function renderNotifs() {
					var list = DATA.notifications;
					var html = "";
					if (list.length) {
						var visibleList = list.slice(0, visibleCount);
						html +=
							'<div class="mn-row mn-between" style="margin-bottom:12px">' +
							'<button class="mn-btn mn-btn--sm mn-btn--secondary" data-act="clear" title="Mark all notifications as read">' +
							icon("check") +
							" Mark all read</button>" +
							'<span class="mn-muted" style="font-size:12px">Showing ' +
							visibleList.length +
							" of " +
							list.length +
							"</span>" +
							"</div>";
						html += '<div class="mn-list">';
						visibleList.forEach((n) => {
							var icName = "info";
							var icCls = "mn-notif__icon--info";
							if (n.type === "success") {
								icName = "check-circle";
								icCls = "mn-notif__icon--success";
							}
							if (n.type === "error") {
								icName = "warning";
								icCls = "mn-notif__icon--error";
							}
							html +=
								'<div class="mn-notif">' +
								'<div class="mn-notif__icon ' +
								icCls +
								'">' +
								icon(icName) +
								"</div>" +
								'<div class="mn-notif__body">' +
								'<p class="mn-notif__title">' +
								esc(n.title) +
								"</p>" +
								'<p class="mn-notif__desc">' +
								esc(n.desc) +
								"</p>" +
								'<p class="mn-notif__desc mn-accent" style="margin-top:4px">' +
								esc(n.channel) +
								"</p>" +
								"</div>" +
								'<span class="mn-notif__time">' +
								relTime(n.time) +
								"</span>" +
								"</div>";
						});
						html += "</div>";
						if (visibleCount < list.length) {
							html +=
								'<div class="mn-row mn-center mn-muted" style="padding:12px 0;font-size:12px">' +
								"Loading…" +
								"</div>";
						} else {
							html +=
								'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px">' +
								'<span style="opacity:0.6">• You have reached the end of the list •</span>' +
								"</div>";
						}
					} else {
						html = stateViewHtml(
							"bell",
							"No notifications",
							"Download completions, new uploads, sync and other events will appear here.",
						);
					}
					render(el, html);
					var clearBtn = qs('[data-act="clear"]', el);
					if (clearBtn) {
						clearBtn.onclick = () => {
							DATA.notifications.length = 0;
							store.set({ notifCount: 0 });
							updateNotifBadge();
							renderNotifs();
							toast("Notifications cleared", "info");
						};
					}
				}

				el.addEventListener("scroll", onScroll);
				renderNotifs();

				return {
					unmount: () => {
						el.removeEventListener("scroll", onScroll);
					},
				};
			},
		});
	}

	function stateViewHtml(iconName, title, desc) {
		return (
			'<div class="mn-state">' +
			'<div class="mn-state__icon">' +
			icon(iconName) +
			"</div>" +
			'<h3 class="mn-state__title">' +
			esc(title) +
			"</h3>" +
			'<p class="mn-state__desc">' +
			esc(desc) +
			"</p>" +
			"</div>"
		);
	}

	/* ==========================================================================
     10. Media-type + link-history helpers
     ======================================================================== */

	function isAudioTrack(v) {
		if (!v) return false;
		var res = String(v.resolution || "").toLowerCase();
		var fmt = String(v.format || "").toLowerCase();
		return (
			res === "audio" ||
			fmt === "audio" ||
			fmt === "audio_extracted" ||
			fmt === "audio_only"
		);
	}

	/* typeBadge(v) -> a compact audio/video icon badge for any media card. */
	function typeBadge(v) {
		var isAudio = isAudioTrack(v);
		return (
			'<span class="mn-type-badge' +
			(isAudio ? " mn-type-badge--audio" : " mn-type-badge--video") +
			'" aria-label="' +
			(isAudio ? "Audio" : "Video") +
			'">' +
			icon(isAudio ? "music" : "video") +
			"</span>"
		);
	}

	/* linkKind(url) -> 'video' | 'playlist' | 'channel' | 'unknown' */
	function linkKind(url) {
		var u = String(url || "");
		if (/\/playlist\?list=|&list=/.test(u)) return "playlist";
		if (/\/watch\?v=|\/shorts\/|youtu\.be\//.test(u)) return "video";
		if (/\/@[a-z0-9_-]+|\/channel\/|\/c\//.test(u)) return "channel";
		return "unknown";
	}

	function refreshAppbar() {
		var def = routes[currentRoute];
		if (def) updateAppbar(def, currentParams);
	}

	/* Re-render the current appbar actions (for dynamic in-screen toggles). */
	function historyCopy(url) {
		try {
			if (navigator.clipboard && navigator.clipboard.writeText) {
				navigator.clipboard.writeText(url).then(
					() => toast("Link copied to clipboard", "info"),
					() => toast("Copy failed", "error"),
				);
			} else {
				toast("Link copied (demo)", "info");
			}
		} catch (e) {
			toast("Copy failed", "error");
		}
	}

	function historyRemove(url) {
		var arr = DATA.linkHistory || [];
		var idx = arr.findIndex((x) => x.url === url);
		if (idx >= 0) arr.splice(idx, 1);
	}

	function historyClear() {
		DATA.linkHistory = [];
	}

	/* ==========================================================================
     10. Public API
     ======================================================================== */

	window.MN = {
		DATA: DATA,
		store: store,
		esc: esc,
		icon: icon,
		h: h,
		render: render,
		domFrom: domFrom,
		qs: qs,
		stateView: stateViewHtml,
		isAudioTrack: isAudioTrack,
		typeBadge: typeBadge,
		linkKind: linkKind,
		refreshAppbar: refreshAppbar,
		history: {
			copy: historyCopy,
			remove: historyRemove,
			clear: historyClear,
		},
		fmt: {
			duration: fmtDuration,
			bytes: fmtBytes,
			speed: fmtSpeed,
			rel: relTime,
		},
		router: {
			navigate: navigate,
			back: back,
			register: registerRoute,
			current: () => ({ route: currentRoute, params: currentParams }),
		},
		toast: toast,
		sheet: sheet,
		closeSheet: closeSheet,
		dialog: dialog,
		closeDialog: closeDialog,
		playback: {
			play: play,
			toggle: togglePlay,
			next: next,
			prev: prev,
			seek: seek,
			seekRelative: seekRelative,
			setAutoplay: setAutoplay,
			setSpeed: setSpeed,
			setQuality: setQuality,
			renderMiniPlayer: renderMiniPlayer,
		},
		downloads: {
			enqueue: enqueueDownload,
			pause: pauseDownload,
			resume: resumeDownload,
			retry: retryDownload,
			cancel: cancelDownload,
			remove: deleteDownload,
			onTick: (fn) => {
				dlListeners.push(fn);
			},
		},
		notify: addNotification,
		boot: bootstrap,
	};
})();

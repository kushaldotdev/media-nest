/* ============================================================================
   MediaNest — Home screen
   URL extraction, playlist/channel results, quick download, link history,
   bulk download flow, mark-watched, move-to-folder, favorites.
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = MN.DATA;

	/* --- per-session UI state ------------------------------------------------ */
	var view = "idle"; // 'idle' | 'loading' | 'playlist' | 'channel'
	var saved = false;
	var subscribed = false;
	var showShorts = false;
	var urlValue = "";
	var rootEl = null;
	var visibleCount = 10;

	var favSet = new Set();
	var watchCounts = {};
	var titleExpanded = new Set();

	DATA.videos.forEach((v) => {
		if (v.favorite) favSet.add(v.id);
		watchCounts[v.id] = v.watchCount || 0;
	});

	/* --- helpers -------------------------------------------------------------- */
	function findFull(pv) {
		return DATA.videos.find((v) => v.title === pv.title);
	}

	function enrich(pv) {
		var full = findFull(pv);
		var id = full ? full.id : pv.id;
		var hist = full ? DATA.history.find((h) => h.videoId === full.id) : null;
		var progress = 0;
		if (hist && full && full.durationSeconds > 0) {
			progress = hist.positionMillis / 1000 / full.durationSeconds;
			if (progress > 1) progress = 1;
		}
		return {
			id: id,
			title: pv.title,
			channelName: pv.channelName,
			durationSeconds: pv.durationSeconds,
			thumbnailUrl: pv.thumbnailUrl,
			uploadDate: pv.uploadDate,
			isShort: !!pv.isShort,
			favorite: favSet.has(id),
			watchCount: watchCounts[id] || 0,
			resolution: full ? full.resolution : "720p",
			localFilePath: full ? full.localFilePath : "",
			progress: progress,
		};
	}

	function sourceRaw() {
		return view === "channel" ? DATA.channel.uploads : DATA.playlist.videos;
	}

	function sourceDisplay() {
		var raw = sourceRaw();
		if (showShorts) return raw;
		return raw.filter((v) => !v.isShort);
	}

	/* --- link history helpers ------------------------------------------------- */
	function linkKindIcon(url) {
		var kind = MN.linkKind(url);
		if (kind === "video") return "video";
		if (kind === "playlist") return "playlist";
		if (kind === "channel") return "channel";
		return "link";
	}

	function linkKindLabel(url) {
		var kind = MN.linkKind(url);
		if (kind === "video") return "Video";
		if (kind === "playlist") return "Playlist";
		if (kind === "channel") return "Channel";
		return "Link";
	}

	function loadLinkHistory(url) {
		var kind = MN.linkKind(url);
		urlValue = url;
		view = "loading";
		renderHome();
		setTimeout(() => {
			showDemo(kind === "channel" ? "channel" : "playlist");
		}, 700);
	}

	/* --- HTML builders -------------------------------------------------------- */
	function heroHtml() {
		return (
			"" +
			'<div class="mn-hero mn-home-hero">' +
			'<p class="mn-hero__eyebrow">Offline-first</p>' +
			'<h2 class="mn-hero__title">MediaNest</h2>' +
			'<p class="mn-hero__desc">Download, organize and play your YouTube library — even offline.</p>' +
			'<div class="mn-home-url">' +
			'<div class="mn-field mn-fill" style="position:relative">' +
			MN.icon("youtube") +
			'<input id="url-input" type="text" inputmode="url" placeholder="Paste YouTube URL" style="padding-right:110px" />' +
			'<button class="mn-btn mn-btn--primary mn-btn--sm mn-field__action" data-act="extract" title="Extract URL">' +
			MN.icon("extract") +
			" Extract</button>" +
			"</div>" +
			"</div>" +
			'<div class="mn-chips" style="margin-top:12px">' +
			'<button class="mn-chip" data-act="demo-playlist" title="Load demo playlist">' +
			MN.icon("playlist") +
			" Demo: Playlist</button>" +
			'<button class="mn-chip" data-act="demo-channel" title="Load demo channel">' +
			MN.icon("channel") +
			" Demo: Channel</button>" +
			"</div>" +
			"</div>"
		);
	}

	function playlistHeaderHtml() {
		var pl = DATA.playlist;
		var desc = pl && pl.description ? pl.description : "";
		var descHtml = desc
			? '<p class="mn-muted" style="margin:4px 0 0;font-size:12px">' +
				MN.esc(desc) +
				"</p>"
			: "";
		var coverUrl =
			pl && pl.thumbnailUrl
				? pl.thumbnailUrl
				: DATA.helpers.thumb(12, pl ? pl.name : "Playlist");
		return (
			"" +
			'<div class="mn-card mn-card--pad" style="margin-bottom:14px">' +
			'<img style="width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block" src="' +
			MN.esc(coverUrl) +
			'" alt=""/>' +
			'<div style="margin-top:10px">' +
			'<p class="mn-key">Playlist</p>' +
			'<h3 style="margin:2px 0;font-size:17px;font-weight:600">' +
			MN.esc(pl.name) +
			"</h3>" +
			'<p class="mn-muted" style="margin:0">Videos: ' +
			pl.videoCount +
			"</p>" +
			descHtml +
			"</div>" +
			'<div class="mn-row mn-gap-2" style="margin-top:12px">' +
			'<button class="mn-btn mn-btn--sm ' +
			(saved ? "mn-btn--secondary" : "mn-btn--primary") +
			' mn-fill" data-act="toggle-save" title="' +
			(saved ? "Remove from Playlist" : "Save Playlist") +
			'">' +
			MN.icon(saved ? "check" : "playlist") +
			(saved ? " Saved to Playlist" : " Add to Playlist") +
			"</button>" +
			'<button class="mn-btn mn-btn--sm mn-btn--deep mn-fill" data-act="download-all" title="Download all playlist videos">' +
			MN.icon("download") +
			" Download All</button>" +
			"</div>" +
			'<div class="mn-row mn-between" style="margin-top:12px">' +
			'<span class="mn-muted" style="font-size:12px">Show Shorts</span>' +
			'<button class="mn-switch ' +
			(showShorts ? "mn-switch--on" : "") +
			'" data-act="toggle-shorts" role="switch" aria-checked="' +
			(showShorts ? "true" : "false") +
			'" title="Toggle Shorts"></button>' +
			"</div>" +
			"</div>"
		);
	}

	function channelHeaderHtml() {
		var ch = DATA.channel;
		var desc = ch && ch.description ? ch.description : "";
		var descHtml = desc
			? '<p class="mn-muted" style="margin:4px 0 0;font-size:12px">' +
				MN.esc(desc) +
				"</p>"
			: "";
		var bannerUrl = DATA.helpers.thumb(2, ch.name);
		return (
			"" +
			'<div class="mn-card mn-card--pad" style="margin-bottom:14px">' +
			'<img style="width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px;display:block" src="' +
			MN.esc(bannerUrl) +
			'" alt=""/>' +
			'<div class="mn-row mn-gap-3" style="margin-top:10px;align-items:center">' +
			'<img style="width:52px;height:52px;border-radius:50%;object-fit:cover" src="' +
			MN.esc(ch.avatarUrl) +
			'" alt=""/>' +
			"<div>" +
			'<p class="mn-key">Channel</p>' +
			'<h3 style="margin:2px 0;font-size:17px;font-weight:600">' +
			MN.esc(ch.name) +
			"</h3>" +
			'<p class="mn-muted" style="margin:0">Videos: ' +
			ch.videoCount +
			"</p>" +
			"</div>" +
			"</div>" +
			descHtml +
			'<div class="mn-row mn-gap-2" style="margin-top:12px">' +
			'<button class="mn-btn mn-btn--sm ' +
			(subscribed ? "mn-btn--secondary" : "mn-btn--deep") +
			' mn-fill" data-act="subscribe" title="' +
			(subscribed ? "Unsubscribe" : "Subscribe") +
			'">' +
			MN.icon(subscribed ? "check" : "youtube") +
			(subscribed ? " Subscribed" : " Subscribe") +
			"</button>" +
			'<button class="mn-btn mn-btn--sm mn-btn--primary mn-fill" data-act="download-all" title="Download all channel uploads">' +
			MN.icon("download") +
			" Download All</button>" +
			"</div>" +
			'<div class="mn-row mn-between" style="margin-top:12px">' +
			'<span class="mn-muted" style="font-size:12px">Show Shorts</span>' +
			'<button class="mn-switch ' +
			(showShorts ? "mn-switch--on" : "") +
			'" data-act="toggle-shorts" role="switch" aria-checked="' +
			(showShorts ? "true" : "false") +
			'" title="Toggle Shorts"></button>' +
			"</div>" +
			"</div>"
		);
	}

	function mediaRowHtml(v, i) {
		var favIconCls = v.favorite ? " mn-icon--filled mn-icon-btn--accent" : "";
		var progress =
			v.progress > 0
				? '<div class="mn-thumb__progress"><span style="width:' +
					Math.round(v.progress * 100) +
					'%"></span></div>'
				: "";
		var watchBadge =
			v.watchCount > 0
				? '<span class="mn-thumb__badge mn-thumb__badge--bl">' +
					MN.icon("eye", "mn-icon--sm") +
					" " +
					v.watchCount +
					"</span>"
				: "";
		var isExpanded = titleExpanded.has(v.id);
		var titleStyle = isExpanded
			? ' style="-webkit-line-clamp:none;overflow:visible"'
			: "";

		return (
			"" +
			'<div class="mn-media-row" data-id="' +
			MN.esc(v.id) +
			'">' +
			'<div class="mn-thumb mn-media-row__thumb">' +
			'<img src="' +
			v.thumbnailUrl +
			'" alt=""/>' +
			MN.typeBadge(v) +
			watchBadge +
			'<span class="mn-thumb__badge mn-thumb__badge--br">' +
			MN.fmt.duration(v.durationSeconds) +
			"</span>" +
			progress +
			"</div>" +
			'<div class="mn-media-row__body">' +
			'<p class="mn-media-row__title" data-title-toggle="' +
			MN.esc(v.id) +
			'"' +
			titleStyle +
			' title="Click to toggle full title">' +
			(i + 1) +
			". " +
			MN.esc(v.title) +
			"</p>" +
			'<div class="mn-media-row__meta"><span>' +
			MN.esc(v.channelName) +
			"</span><span>" +
			MN.esc(v.uploadDate) +
			"</span></div>" +
			'<div class="mn-media-row__actions">' +
			'<button class="mn-icon-btn mn-icon-btn--sm' +
			favIconCls +
			'" data-act="fav" aria-label="Favorite" title="' +
			(v.favorite ? "Remove from favorites" : "Add to favorites") +
			'">' +
			MN.icon("heart") +
			"</button>" +
			'<button class="mn-icon-btn mn-icon-btn--sm" data-act="more" aria-label="More options" title="More options">' +
			MN.icon("more") +
			"</button>" +
			"</div>" +
			"</div>" +
			"</div>"
		);
	}

	function mediaListHtml(videos) {
		var html = '<div class="mn-list">';
		videos.forEach((pv, i) => {
			html += mediaRowHtml(enrich(pv), i);
		});
		html += "</div>";
		return html;
	}

	function loadingHtml() {
		var html =
			'<p class="mn-key" style="margin:14px 0 8px">Extracting…</p><div class="mn-list">';
		var i;
		for (i = 0; i < 4; i++) {
			html +=
				"" +
				'<div class="mn-media-row" style="pointer-events:none">' +
				'<span class="mn-skeleton" style="width:128px;height:72px;border-radius:8px;flex:0 0 auto"></span>' +
				'<div class="mn-media-row__body" style="gap:8px">' +
				'<span class="mn-skeleton" style="height:15px;width:82%"></span>' +
				'<span class="mn-skeleton" style="height:11px;width:50%"></span>' +
				"</div>" +
				"</div>";
		}
		html += "</div>";
		return html;
	}

	function historyHtml() {
		var items = DATA.linkHistory;
		if (!items.length) {
			return (
				'<div class="mn-section-title"><h2>History</h2></div>' +
				MN.stateView(
					"history",
					"No link history",
					"Links you extract will appear here for quick re-load.",
				)
			);
		}
		var visibleItems = items.slice(0, visibleCount);
		var html =
			'<div class="mn-section-title"><h2>History</h2>' +
			'<button class="mn-section-title__action" data-act="history-clear" style="color:var(--mn-destructive)" title="Clear all history">' +
			MN.icon("trash", "mn-icon--sm") +
			" Clear all</button></div>";
		html += '<div class="mn-list">';
		visibleItems.forEach((it) => {
			var kindLabel = linkKindLabel(it.url);
			var kindIcon = linkKindIcon(it.url);
			html +=
				"" +
				'<div class="mn-card mn-history-row" data-act="history-copy" data-url="' +
				MN.esc(it.url) +
				'" title="Copy link">' +
				'<span class="mn-history-row__type">' +
				MN.icon(kindIcon) +
				"</span>" +
				'<div class="mn-fill" style="min-width:0">' +
				'<p class="mn-media-row__title" style="font-size:13px">' +
				MN.esc(it.title) +
				"</p>" +
				'<p class="mn-muted mn-truncate" style="font-size:11px;margin:2px 0 0">' +
				MN.esc(kindLabel) +
				" · " +
				MN.esc(it.url) +
				"</p>" +
				"</div>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" data-act="history-load" data-url="' +
				MN.esc(it.url) +
				'" aria-label="Load link" title="Load link">' +
				MN.icon("play") +
				"</button>" +
				'<button class="mn-icon-btn mn-icon-btn--sm" data-act="history-delete" data-url="' +
				MN.esc(it.url) +
				'" aria-label="Delete history item" style="color:var(--mn-destructive)" title="Delete history item">' +
				MN.icon("trash") +
				"</button>" +
				"</div>";
		});
		html += "</div>";
		if (visibleCount >= items.length && items.length > 0) {
			html +=
				'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
		}
		return html;
	}

	function resultHtml() {
		if (view === "loading") return loadingHtml();
		if (view === "playlist" || view === "channel") {
			var list = sourceDisplay();
			var header =
				view === "playlist" ? playlistHeaderHtml() : channelHeaderHtml();
			var html = header + mediaListHtml(list.slice(0, visibleCount));
			if (visibleCount >= list.length && list.length > 0) {
				html +=
					'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
			}
			return html;
		}
		return (
			'<div style="margin-top:8px">' +
			MN.stateView(
				"search",
				"Paste a URL to begin",
				"Extract a video, audio, playlist or channel link to get started.",
			) +
			"</div>"
		);
	}

	/* --- render --------------------------------------------------------------- */
	function renderHome() {
		if (!rootEl) return;
		MN.render(rootEl, heroHtml() + resultHtml() + historyHtml());
		var input = MN.qs("#url-input", rootEl);
		if (input) {
			input.value = urlValue;
			input.oninput = () => {
				urlValue = input.value;
			};
		}
	}

	/* --- actions -------------------------------------------------------------- */
	function showDemo(kind) {
		view = kind === "channel" ? "channel" : "playlist";
		showShorts = false;
		saved = false;
		subscribed = false;
		visibleCount = 10;
		renderHome();
		MN.toast(kind === "channel" ? "Channel loaded" : "Playlist loaded", "info");
	}

	function doExtract() {
		if (!urlValue.trim()) {
			MN.toast("Paste a YouTube URL first", "error");
			return;
		}
		view = "loading";
		visibleCount = 10;
		renderHome();
		setTimeout(() => {
			showDemo("playlist");
		}, 800);
	}

	function toggleFav(id) {
		var on = !favSet.has(id);
		if (on) favSet.add(id);
		else favSet.delete(id);
		var full = DATA.videos.find((x) => x.id === id);
		if (full) full.favorite = on;
		renderHome();
		MN.toast(on ? "Added to favorites" : "Removed from favorites", "info");
	}

	function openMoreSheet(id) {
		var v = activeById[id];
		if (!v) return;
		var body = "";
		body +=
			'<button class="mn-setting-row" data-moveto="' +
			v.id +
			'" title="Move to folder">' +
			'<span class="mn-setting-row__icon">' +
			MN.icon("move") +
			"</span>" +
			'<span class="mn-setting-row__body"><span class="mn-setting-row__title">Move to folder</span></span>' +
			"</button>";
		body +=
			'<button class="mn-setting-row" data-dl="' +
			v.id +
			'" title="Download video">' +
			'<span class="mn-setting-row__icon">' +
			MN.icon("download") +
			"</span>" +
			'<span class="mn-setting-row__body"><span class="mn-setting-row__title">Download</span></span>' +
			"</button>";
		body +=
			'<button class="mn-setting-row" data-watch="' +
			v.id +
			'" title="Mark as watched">' +
			'<span class="mn-setting-row__icon">' +
			MN.icon("eye") +
			"</span>" +
			'<span class="mn-setting-row__body"><span class="mn-setting-row__title">Mark as watched</span></span>' +
			"</button>";
		MN.sheet({
			title: MN.esc(v.title),
			body: body,
			onOpen: (bodyEl) => {
				var move = MN.qs("[data-moveto]", bodyEl);
				if (move)
					move.onclick = () => {
						openMoveSheet(v.id);
					};
				var dl = MN.qs("[data-dl]", bodyEl);
				if (dl)
					dl.onclick = () => {
						MN.closeSheet();
						var defaultRes = MN.store.get().defaultResolution || "360p";
						var isAudio = defaultRes === "Audio";
						MN.downloads.enqueue(
							v,
							isAudio ? "audio" : "video",
							isAudio ? "128kbps (opus)" : defaultRes,
						);
						MN.toast("Download started (" + defaultRes + ")", "success");
					};
				var watch = MN.qs("[data-watch]", bodyEl);
				if (watch)
					watch.onclick = () => {
						MN.closeSheet();
						openWatchDialog(v.id);
					};
			},
		});
	}

	function openMoveSheet(id) {
		var v = activeById[id];
		if (!v) return;
		var folders = DATA.folders;
		var html;
		if (folders.length) {
			html = '<div class="mn-list">';
			folders.forEach((f) => {
				html +=
					"" +
					'<button class="mn-setting-row" style="width:100%" data-folder="' +
					f.id +
					'" title="Select folder ' +
					MN.esc(f.name) +
					'">' +
					'<span class="mn-setting-row__icon">' +
					MN.icon("folder") +
					"</span>" +
					'<span class="mn-setting-row__body"><span class="mn-setting-row__title">' +
					MN.esc(f.name) +
					"</span></span>" +
					MN.icon("chevron-right") +
					"</button>";
			});
			html += "</div>";
		} else {
			html = MN.stateView(
				"folder",
				"No folders",
				"Create one in the Collections tab first.",
			);
		}
		MN.sheet({
			title: "Move to Folder",
			body: html,
			onOpen: (body) => {
				body.querySelectorAll("[data-folder]").forEach((btn) => {
					btn.onclick = () => {
						var fid = parseInt(btn.getAttribute("data-folder"), 10);
						var f = folders.find((x) => x.id === fid);
						var arr = DATA.videoFolderMap[id] || [];
						if (!arr.some((x) => x.id === fid)) arr.push(f);
						DATA.videoFolderMap[id] = arr;
						MN.closeSheet();
						MN.toast("Moved to " + (f ? f.name : "folder"), "success");
					};
				});
			},
		});
	}

	function estSize(sec, quality) {
		var bitrate = 2500000;
		if (quality === "1080p") bitrate = 4500000;
		else if (quality === "720p") bitrate = 2500000;
		else if (quality === "480p") bitrate = 1200000;
		else if (quality === "360p") bitrate = 800000;
		else if (quality === "Audio") bitrate = 128000;
		return Math.round((sec * bitrate) / 8);
	}

	function streamOptions(v) {
		var opts = [];
		var isAudioOnly = v.resolution === "Audio";
		if (!isAudioOnly) {
			var base = parseInt(v.resolution, 10) || 720;
			var seen = {};
			var resList = [];
			[base, 720, 480, 360].forEach((r) => {
				if (!seen[r]) {
					seen[r] = true;
					resList.push(r);
				}
			});
			resList.forEach((r) => {
				opts.push({
					key: "v" + r,
					format: "video",
					quality: r + "p",
					label: r + "p (vp9)",
					size: estSize(v.durationSeconds, r + "p"),
				});
			});
		}
		opts.push({
			key: "audio",
			format: "audio",
			quality: "128kbps (opus)",
			label: "Audio Only (128kbps)",
			size: estSize(v.durationSeconds, "Audio"),
		});
		return opts;
	}

	function openQuickDownload(id) {
		var v = activeById[id];
		if (!v) return;
		var existing = DATA.downloads.filter((d) => d.videoId === id);
		var html = "";
		if (existing.length) {
			html += '<p class="mn-key" style="margin:4px 0 8px">Downloaded Formats</p>';
			existing.forEach((d) => {
				var label =
					d.format === "audio" || d.format === "audio_extracted"
						? "Audio"
						: d.quality;
				var extractBtn =
					d.format === "video" || d.format === "video_only"
						? '<button class="mn-icon-btn mn-icon-btn--sm" data-dl="extract" data-dlid="' +
							d.id +
							'" aria-label="Extract audio" title="Extract audio">' +
							MN.icon("music") +
							"</button>"
						: "";
				html +=
					"" +
					'<div class="mn-row mn-between" style="padding:6px 0">' +
					"<span>" +
					MN.esc(label) +
					' <span class="mn-tag mn-tag--success">' +
					d.status +
					"</span></span>" +
					'<div class="mn-row mn-gap-1">' +
					extractBtn +
					'<button class="mn-icon-btn mn-icon-btn--sm" data-dl="delete" data-dlid="' +
					d.id +
					'" aria-label="Delete download" title="Delete download">' +
					MN.icon("trash") +
					"</button>" +
					"</div>" +
					"</div>";
			});
			html += '<hr class="mn-divider"/>';
		}
		html += '<p class="mn-key" style="margin:8px 0">Available Videos</p>';
		var streams = streamOptions(v);
		streams.forEach((s) => {
			html +=
				"" +
				'<button class="mn-stream-row" style="width:100%" data-stream="' +
				s.key +
				'" title="Download ' +
				MN.esc(s.label) +
				'">' +
				'<span class="mn-row mn-gap-2">' +
				MN.icon(s.format === "audio" ? "music" : "video") +
				"<span>" +
				MN.esc(s.label) +
				"</span></span>" +
				'<span class="mn-setting-row__value">' +
				MN.fmt.bytes(s.size) +
				"</span>" +
				"</button>";
		});

		MN.sheet({
			title: "Download",
			body: html,
			onOpen: (body) => {
				body.querySelectorAll("[data-dl]").forEach((btn) => {
					btn.onclick = () => {
						var dlId = parseInt(btn.getAttribute("data-dlid"), 10);
						var d = DATA.downloads.find((x) => x.id === dlId);
						if (!d) return;
						if (btn.getAttribute("data-dl") === "delete") {
							MN.downloads.remove(d);
							MN.toast("Download removed", "info");
						} else {
							MN.downloads.enqueue(v, "audio", "128kbps (m4a)");
							MN.toast("Audio extraction started", "success");
						}
						MN.closeSheet();
					};
				});
				body.querySelectorAll("[data-stream]").forEach((btn) => {
					btn.onclick = () => {
						var key = btn.getAttribute("data-stream");
						var s = streams.find((x) => x.key === key);
						if (!s) return;
						MN.closeSheet();
						MN.downloads.enqueue(v, s.format, s.quality);
						MN.toast("Added to download queue", "info");
					};
				});
			},
		});
	}

	function openWatchDialog(id) {
		var v = activeById[id];
		if (!v) return;
		var current = watchCounts[id] || 0;
		MN.dialog({
			title: "Set Watched",
			body:
				"" +
				'<p class="mn-muted">' +
				MN.esc(v.title) +
				"</p>" +
				'<div class="mn-field" style="margin-top:12px">' +
				MN.icon("eye") +
				'<input id="watch-input" type="number" min="0" step="1" inputmode="numeric" value="' +
				current +
				'" />' +
				"</div>",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Save",
					cls: "mn-btn--primary",
					onClick: () => {
						var input = MN.qs("#watch-input");
						var n = input ? parseInt(input.value, 10) : 0;
						if (Number.isNaN(n) || n < 0) n = 0;
						watchCounts[id] = n;
						var full = DATA.videos.find((x) => x.id === id);
						if (full) full.watchCount = n;
						renderHome();
						MN.toast("Watch count set to " + n, "success");
					},
				},
			],
		});
	}

	function openBulkQuality() {
		var qualities = ["1080p", "720p", "480p", "360p", "Audio"];
		var html = '<div class="mn-list">';
		qualities.forEach((q) => {
			html +=
				"" +
				'<button class="mn-setting-row" style="width:100%" data-q="' +
				q +
				'" title="Download all at ' +
				MN.esc(q) +
				'">' +
				'<span class="mn-setting-row__icon">' +
				MN.icon(q === "Audio" ? "music" : "video") +
				"</span>" +
				'<span class="mn-setting-row__body"><span class="mn-setting-row__title">' +
				q +
				"</span></span>" +
				MN.icon("chevron-right") +
				"</button>";
		});
		html += "</div>";
		MN.sheet({
			title: "Download All by Resolution",
			body: html,
			onOpen: (body) => {
				body.querySelectorAll("[data-q]").forEach((btn) => {
					btn.onclick = () => {
						var q = btn.getAttribute("data-q");
						MN.closeSheet();
						runBulk(q);
					};
				});
			},
		});
	}

	function runBulk(quality) {
		var list = sourceDisplay();
		var total = list.length;
		var cur = 0;
		MN.dialog({
			title: "Fetching Video Metadata",
			body: progressBody(0, total),
			dismissible: false,
		});
		var timer = setInterval(() => {
			cur++;
			var body = MN.qs("#dialog .mn-dialog__body");
			if (body) MN.render(body, progressBody(cur, total));
			if (cur >= total) {
				clearInterval(timer);
				MN.closeDialog();
				showBulkConfirm(quality, list);
			}
		}, 220);
	}

	function progressBody(cur, total) {
		var pct = total ? Math.round((cur / total) * 100) : 0;
		return (
			"" +
			'<p class="mn-muted">Retrieving stream details to calculate size and check disk space.</p>' +
			'<p style="margin:10px 0 6px">Progress: ' +
			cur +
			" of " +
			total +
			" videos</p>" +
			'<div class="mn-progress"><span style="width:' +
			pct +
			'%"></span></div>'
		);
	}

	function showBulkConfirm(quality, list) {
		var totalSize = 0;
		list.forEach((pv) => {
			totalSize += estSize(pv.durationSeconds, quality);
		});
		MN.dialog({
			title: "Confirm Bulk Download",
			body:
				"" +
				"<p>Quality: <strong>" +
				MN.esc(quality) +
				"</strong></p>" +
				"<p>Total Videos: <strong>" +
				list.length +
				"</strong></p>" +
				"<p>Total Download Size: <strong>" +
				MN.fmt.bytes(totalSize) +
				"</strong></p>" +
				'<p class="mn-success" style="margin-top:10px">' +
				MN.icon("check-circle", "mn-icon--sm") +
				" Storage check: sufficient space available.</p>",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost" },
				{
					label: "Download",
					cls: "mn-btn--primary",
					onClick: () => {
						var isAudio = quality === "Audio";
						list.forEach((pv) => {
							var v = enrich(pv);
							MN.downloads.enqueue(
								v,
								isAudio ? "audio" : "video",
								isAudio ? "128kbps (opus)" : quality,
							);
						});
						MN.toast("Bulk download started", "success");
					},
				},
			],
		});
	}

	function confirmDeleteHistory(url) {
		MN.dialog({
			title: "Delete history item",
			body: "Remove this link from your history?",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
				{
					label: "Delete",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						MN.history.remove(url);
						renderHome();
						MN.toast("History item removed", "info");
					},
				},
			],
		});
	}

	function confirmClearHistory() {
		MN.dialog({
			title: "Clear link history",
			body: "Remove all link history entries? This cannot be undone.",
			actions: [
				{ label: "Cancel", cls: "mn-btn--ghost", onClick: () => {} },
				{
					label: "Clear all",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						MN.history.clear();
						renderHome();
						MN.toast("Link history cleared", "success");
					},
				},
			],
		});
	}

	/* --- delegated action dispatcher ------------------------------------------ */
	var activeById = {};

	function handleAction(act, id, btn) {
		if (act === "extract") doExtract();
		else if (act === "demo-playlist") showDemo("playlist");
		else if (act === "demo-channel") showDemo("channel");
		else if (act === "toggle-save") {
			saved = !saved;
			renderHome();
			MN.toast(saved ? "Added to Playlist" : "Removed from Playlist", "info");
		} else if (act === "subscribe") {
			subscribed = !subscribed;
			renderHome();
			MN.toast(
				subscribed ? "Subscribed to channel" : "Unsubscribed from channel",
				"info",
			);
		} else if (act === "download-all") openBulkQuality();
		else if (act === "toggle-shorts") {
			showShorts = !showShorts;
			visibleCount = 10;
			renderHome();
		} else if (act === "fav") toggleFav(id);
		else if (act === "more") openMoreSheet(id);
		else if (act === "history-copy")
			MN.history.copy(btn.getAttribute("data-url"));
		else if (act === "history-load")
			loadLinkHistory(btn.getAttribute("data-url"));
		else if (act === "history-delete")
			confirmDeleteHistory(btn.getAttribute("data-url"));
		else if (act === "history-clear") confirmClearHistory();
	}

	function onScroll() {
		if (MN.router.current().route !== "home") return;
		if (!rootEl) return;
		var resultsLen =
			view === "playlist" || view === "channel" ? sourceDisplay().length : 0;
		var historyLen = DATA.linkHistory ? DATA.linkHistory.length : 0;
		var maxLen = Math.max(resultsLen, historyLen);
		if (visibleCount >= maxLen) return;
		if (rootEl.scrollHeight - rootEl.scrollTop - rootEl.clientHeight < 80) {
			visibleCount += 10;
			renderHome();
		}
	}

	function onDocClick(e) {
		if (MN.router.current().route !== "home") return;
		var btn = e.target.closest("[data-act]");
		if (btn) {
			var act = btn.getAttribute("data-act");
			var row = btn.closest(".mn-media-row");
			var id = row ? row.getAttribute("data-id") : null;
			handleAction(act, id, btn);
			return;
		}
		var titleToggle = e.target.closest("[data-title-toggle]");
		if (titleToggle) {
			var tid = titleToggle.getAttribute("data-title-toggle");
			if (tid) {
				if (titleExpanded.has(tid)) titleExpanded.delete(tid);
				else titleExpanded.add(tid);
				renderHome();
				return;
			}
		}
		// Clicking a media row body (not a button) opens the video detail page.
		var mediaRow = e.target.closest(".mn-media-row");
		if (mediaRow) {
			var mid = mediaRow.getAttribute("data-id");
			if (mid) MN.router.navigate("video-detail/" + mid);
		}
	}

	/* --- refresh active-by-id map (called after every render) ----------------- */
	function rebuildActiveMap() {
		activeById = {};
		sourceDisplay().forEach((pv) => {
			var v = enrich(pv);
			activeById[v.id] = v;
		});
	}

	/* Wrap renderHome so the id map always matches the latest list. */
	var baseRenderHome = renderHome;
	renderHome = () => {
		rebuildActiveMap();
		baseRenderHome();
	};

	/* --- registration --------------------------------------------------------- */
	MN.router.register("home", {
		title: "MediaNest",
		back: false,
		mount: (el) => {
			rootEl = el;
			if (!el.__homeWired) {
				el.addEventListener("click", onDocClick);
				el.addEventListener("scroll", onScroll);
				el.__homeWired = true;
			}
			renderHome();
			return {};
		},
	});
})();

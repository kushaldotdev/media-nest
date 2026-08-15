/* ============================================================================
   MediaNest — Downloads screen
   Registered route: 'downloads'
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = MN.DATA;

	var viewEl = null;
	var sortCat = "date";
	var sortDir = "desc";
	var tickBound = false;
	var visibleCount = 10;

	var SORT_OPTIONS = [
		{ key: "date", label: "Date" },
		{ key: "progress", label: "Progress" },
		{ key: "size", label: "Size" },
		{ key: "status", label: "Status" },
	];

	var STATUS_ORDER = {
		DOWNLOADING: 0,
		QUEUED: 1,
		PAUSED: 2,
		FAILED: 3,
		CANCELED: 4,
		COMPLETED: 5,
	};

	var STATUS_META = {
		QUEUED: { label: "Queued", cls: "" },
		DOWNLOADING: { label: "Downloading", cls: " mn-tag--accent" },
		PAUSED: { label: "Paused", cls: "" },
		COMPLETED: { label: "Completed", cls: " mn-tag--success" },
		FAILED: { label: "Failed", cls: " mn-tag--error" },
		CANCELED: { label: "Canceled", cls: "" },
	};

	/* ---- data lookups ------------------------------------------------------ */

	function findVideo(videoId) {
		return DATA.videos.find((v) => v.id === videoId) || null;
	}

	function findDownload(id) {
		return DATA.downloads.find((d) => d.id === id) || null;
	}

	function isAudioDownload(dl) {
		return (
			dl.format === "audio" ||
			dl.format === "audio_extracted" ||
			dl.format === "audio_only"
		);
	}

	/* ---- playback bridge ---------------------------------------------------- */

	function toTrack(dl) {
		var v = findVideo(dl.videoId);
		return {
			id: dl.videoId,
			title: dl.title,
			channelName: v ? v.channelName : "",
			durationSeconds: v ? v.durationSeconds : 0,
			thumbnailUrl: dl.thumbnailUrl,
			isLocal: true,
			resolution: dl.quality,
			format: dl.format,
		};
	}

	function playableQueue() {
		var arr = [];
		DATA.downloads.forEach((dl) => {
			if (dl.status === "COMPLETED" && dl.progress >= 1) {
				arr.push({ dl: dl, track: toTrack(dl) });
			}
		});
		return arr;
	}

	function playDownload(dl) {
		var arr = playableQueue();
		var queue = arr.map((x) => x.track);
		var idx = 0;
		for (var i = 0; i < arr.length; i++) {
			if (arr[i].dl === dl) idx = i;
		}
		if (queue.length === 0) {
			queue = [toTrack(dl)];
			idx = 0;
		}
		MN.playback.play(queue, idx);
		render();
	}

	/* ---- resume watching ---------------------------------------------------- */

	function resumeCandidate() {
		var best = null;
		DATA.downloads.forEach((dl) => {
			if (dl.status !== "COMPLETED") return;
			var h = DATA.history.find(
				(x) => x.videoId === dl.videoId && x.positionMillis > 0,
			);
			if (h) {
				if (!best || dl.downloadedAt > best.dl.downloadedAt) {
					best = { dl: dl, pos: h.positionMillis };
				}
			}
		});
		return best;
	}

	/* ---- statistics --------------------------------------------------------- */

	function statsByStatus() {
		var counts = {
			DOWNLOADING: 0,
			QUEUED: 0,
			PAUSED: 0,
			FAILED: 0,
			CANCELED: 0,
			COMPLETED: 0,
		};
		var totalBytes = 0;
		var completedBytes = 0;
		DATA.downloads.forEach((dl) => {
			if (counts[dl.status] != null) counts[dl.status]++;
			totalBytes += dl.fileSizeBytes || 0;
			if (dl.status === "COMPLETED") completedBytes += dl.fileSizeBytes || 0;
		});
		return {
			counts: counts,
			totalBytes: totalBytes,
			completedBytes: completedBytes,
		};
	}

	function statsHtml() {
		var s = statsByStatus();
		var c = s.counts;
		var total = DATA.downloads.length;
		return (
			'<div class="mn-card mn-card--pad" style="margin-bottom:14px">' +
			'<div class="mn-row mn-gap-2" style="margin-bottom:8px">' +
			MN.icon("chart", "mn-icon--sm mn-accent") +
			'<span class="mn-key">Downloads</span>' +
			'<span class="mn-num mn-muted" style="font-size:var(--mn-fs-meta)">' +
			total +
			" items · " +
			MN.fmt.bytes(s.totalBytes) +
			" total</span>" +
			"</div>" +
			'<div class="mn-row mn-wrap" style="gap:6px">' +
			statPill("downloading", c.DOWNLOADING, "mn-tag--accent") +
			statPill("queued", c.QUEUED, "") +
			statPill("paused", c.PAUSED, "") +
			statPill("failed", c.FAILED, "mn-tag--error") +
			statPill("completed", c.COMPLETED, "mn-tag--success") +
			"</div>" +
			'<div class="mn-row mn-between" style="margin-top:10px">' +
			'<span class="mn-muted" style="font-size:var(--mn-fs-meta)">Completed size</span>' +
			'<span class="mn-num">' +
			MN.fmt.bytes(s.completedBytes) +
			"</span>" +
			"</div>" +
			"</div>"
		);
	}

	function statPill(label, count, cls) {
		return (
			'<span class="mn-tag' +
			(cls || "") +
			'">' +
			MN.esc(label) +
			" · " +
			count +
			"</span>"
		);
	}

	/* ---- sorting ------------------------------------------------------------ */

	function sortDownloads(list, cat, dir) {
		var c = cat || sortCat;
		var d = dir || sortDir;
		var arr = list.slice();
		var isAsc = d === "asc";
		arr.sort((a, b) => {
			if (c === "date") {
				return isAsc
					? a.downloadedAt - b.downloadedAt
					: b.downloadedAt - a.downloadedAt;
			}
			if (c === "progress") {
				var pA = a.progress || 0;
				var pB = b.progress || 0;
				return isAsc ? pA - pB : pB - pA;
			}
			if (c === "size") {
				var sA = a.fileSizeBytes || 0;
				var sB = b.fileSizeBytes || 0;
				return isAsc ? sA - sB : sB - sA;
			}
			if (c === "status") {
				var stA = STATUS_ORDER[a.status] == null ? 9 : STATUS_ORDER[a.status];
				var stB = STATUS_ORDER[b.status] == null ? 9 : STATUS_ORDER[b.status];
				return isAsc ? stB - stA : stA - stB;
			}
			return 0;
		});
		return arr;
	}

	function sortLabel() {
		var s = SORT_OPTIONS.find((x) => x.key === sortCat);
		return s ? s.label : "Date";
	}

	function sortIconName() {
		return sortDir === "asc" ? "arrow-up" : "arrow-down";
	}

	/* ---- shared actions ------------------------------------------------------ */

	function pauseAll() {
		var n = 0;
		DATA.downloads.forEach((dl) => {
			if (dl.status === "DOWNLOADING" || dl.status === "QUEUED") {
				MN.downloads.pause(dl);
				n++;
			}
		});
		MN.toast(
			n ? "Paused " + n + " download" + (n === 1 ? "" : "s") : "Nothing to pause",
			"info",
		);
		render();
	}

	function resumeAll() {
		var n = 0;
		DATA.downloads.forEach((dl) => {
			if (dl.status === "PAUSED") {
				MN.downloads.resume(dl);
				n++;
			}
		});
		MN.toast(
			n
				? "Resumed " + n + " download" + (n === 1 ? "" : "s")
				: "Nothing to resume",
			"info",
		);
		render();
	}

	function clearAll(deleteFiles) {
		DATA.downloads.length = 0;
		MN.toast(
			deleteFiles ? "Removed list entries and files" : "Removed list entries",
			"success",
		);
		render();
	}

	function confirmDeleteAll() {
		MN.dialog({
			title: "Delete all downloads",
			body: "This cancels active downloads and removes every entry from the list.",
			actions: [
				{
					label: "List Only",
					cls: "mn-btn--ghost",
					onClick: () => {
						clearAll(false);
					},
				},
				{
					label: "Delete Files & List",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						clearAll(true);
					},
				},
			],
		});
	}

	function confirmCancel(dl) {
		MN.dialog({
			title: "Cancel download",
			body: 'Cancel downloading "' + MN.esc(dl.title) + '"?',
			actions: [
				{ label: "Keep", cls: "mn-btn--ghost", onClick: () => {} },
				{
					label: "Cancel Download",
					cls: "mn-btn--danger",
					onClick: () => {
						MN.downloads.cancel(dl);
						render();
					},
				},
			],
		});
	}

	function confirmRestart(dl) {
		MN.dialog({
			title: "Restart download",
			body:
				'Restart "' +
				MN.esc(dl.title) +
				'" from scratch? Any partial files will be removed.',
			actions: [
				{ label: "Keep", cls: "mn-btn--ghost", onClick: () => {} },
				{
					label: "Restart",
					cls: "mn-btn--primary",
					onClick: () => {
						MN.downloads.retry(dl);
						render();
					},
				},
			],
		});
	}

	function confirmDelete(dl) {
		MN.dialog({
			title: "Delete download",
			body: 'Choose how to delete "' + MN.esc(dl.title) + '".',
			actions: [
				{
					label: "List Only",
					cls: "mn-btn--ghost",
					onClick: () => {
						MN.downloads.remove(dl);
						render();
					},
				},
				{
					label: "Delete File & List",
					cls: "mn-btn--danger-solid",
					onClick: () => {
						MN.downloads.remove(dl);
						render();
					},
				},
			],
		});
	}

	function extractAudio(dl) {
		MN.toast("Extracting audio from " + MN.esc(dl.quality), "info");
		setTimeout(() => {
			var size = Math.round(dl.fileSizeBytes * 0.08);
			DATA.downloads.unshift({
				id: DATA.downloads.length + 1,
				videoId: dl.videoId,
				title: dl.title,
				thumbnailUrl: dl.thumbnailUrl,
				format: "audio_extracted",
				quality: "128kbps (m4a)",
				status: "COMPLETED",
				progress: 1,
				fileSizeBytes: size,
				downloadedBytes: size,
				speedBytesPerSec: 0,
				elapsedMs: 0,
				remainingMs: 0,
				filePath: "",
				downloadedAt: Date.now(),
				errorMessage: null,
			});
			MN.notify({
				type: "success",
				title: "Audio extracted",
				desc: dl.title,
				channel: "Downloads",
			});
			render();
		}, 1400);
	}

	/* ---- max concurrent / sort sheets --------------------------------------- */

	function openMaxSheet() {
		var cur = MN.store.get().maxConcurrent;
		var body = "";
		for (var n = 1; n <= 5; n++) {
			body +=
				'<button class="mn-select__option' +
				(n === cur ? " mn-select__option--active" : "") +
				'" data-max="' +
				n +
				'" title="Set max concurrent downloads to ' +
				n +
				'">' +
				"<span>" +
				n +
				" concurrent</span>" +
				(n === cur ? MN.icon("check") : "") +
				"</button>";
		}
		MN.sheet({
			title: "Max concurrent downloads",
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-max]").forEach((btn) => {
					btn.onclick = () => {
						var val = parseInt(btn.getAttribute("data-max"), 10);
						MN.store.set({ maxConcurrent: val });
						MN.closeSheet();
						MN.toast("Max concurrent: " + val, "info");
						render();
					};
				});
			},
		});
	}

	function openSortSheet() {
		var body = "";
		SORT_OPTIONS.forEach((o) => {
			var active = sortCat === o.key;
			var iconName = active
				? sortDir === "asc"
					? "arrow-up"
					: "arrow-down"
				: "arrow-down";
			body +=
				'<button class="mn-select__option' +
				(active ? " mn-select__option--active" : "") +
				'" data-sort="' +
				o.key +
				'" title="Sort by ' +
				MN.esc(o.label) +
				'">' +
				'<span class="mn-row mn-gap-2">' +
				MN.icon(iconName, "mn-icon--sm") +
				"<span>" +
				MN.esc(o.label) +
				"</span></span>" +
				(active ? MN.icon("check") : "") +
				"</button>";
		});
		MN.sheet({
			title: "Sort downloads",
			body: body,
			onOpen: (bodyEl) => {
				bodyEl.querySelectorAll("[data-sort]").forEach((btn) => {
					btn.onclick = () => {
						var cat = btn.getAttribute("data-sort");
						if (sortCat === cat) {
							sortDir = sortDir === "desc" ? "asc" : "desc";
						} else {
							sortCat = cat;
							sortDir = "desc";
						}
						MN.closeSheet();
						render();
					};
				});
			},
		});
	}

	/* ---- render helpers ------------------------------------------------------ */

	function actBtn(act, iconName, label, cls, id) {
		var ic = iconName ? MN.icon(iconName) : "";
		var extraCls = "";
		if (cls && cls.includes("mn-btn--primary"))
			extraCls += " mn-icon-btn--accent";
		if (cls && cls.includes("mn-btn--danger")) extraCls += " mn-error";
		return (
			'<button class="mn-icon-btn mn-icon-btn--sm' +
			extraCls +
			'" data-act="' +
			act +
			'" data-id="' +
			id +
			'" title="' +
			MN.esc(label) +
			'">' +
			ic +
			"</button>"
		);
	}

	function statusBadge(dl) {
		var s = dl ? dl.status : "";
		if (s === "COMPLETED") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr mn-thumb__badge--success" title="Status: Completed" data-act="status-toast" data-msg="Status: Completed">' +
				MN.icon("check-circle", "mn-icon--sm mn-success") +
				"</span>"
			);
		}
		if (s === "FAILED") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr mn-thumb__badge--error" title="Status: Failed" data-act="status-toast" data-msg="Status: Failed — ' +
				MN.esc((dl && dl.errorMessage) || "") +
				'">' +
				MN.icon("warning", "mn-icon--sm mn-error") +
				"</span>"
			);
		}
		if (s === "DOWNLOADING") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr mn-thumb__badge--accent" title="Status: Downloading" data-act="status-toast" data-msg="Status: Downloading">' +
				MN.icon("download", "mn-icon--sm mn-accent") +
				"</span>"
			);
		}
		if (s === "PAUSED") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr" title="Status: Paused" data-act="status-toast" data-msg="Status: Paused">' +
				MN.icon("pause", "mn-icon--sm") +
				"</span>"
			);
		}
		if (s === "QUEUED") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr" title="Status: Queued" data-act="status-toast" data-msg="Status: Queued">' +
				MN.icon("history", "mn-icon--sm") +
				"</span>"
			);
		}
		if (s === "CANCELED") {
			return (
				'<span class="mn-thumb__badge mn-thumb__badge--tr" title="Status: Canceled" data-act="status-toast" data-msg="Status: Canceled">' +
				MN.icon("close", "mn-icon--sm") +
				"</span>"
			);
		}
		return "";
	}

	function typeBadgeFor(dl) {
		return MN.typeBadge({ format: dl.format });
	}

	function getProgressClass(dl) {
		if (dl.errorMessage && dl.errorMessage.indexOf("merging") === 0) {
			return "mn-progress mn-progress--merge";
		}
		if (
			dl.format === "audio" ||
			dl.format === "audio_extracted" ||
			dl.format === "audio_only"
		) {
			return "mn-progress mn-progress--audio";
		}
		return "mn-progress mn-progress--video";
	}

	function progressBlock(dl) {
		var pct = Math.round((dl.progress || 0) * 100);
		var pClass = getProgressClass(dl);
		var html =
			'<div class="' +
			pClass +
			'" style="margin-top:2px"><span style="width:' +
			pct +
			'%"></span></div>';
		html +=
			'<div class="mn-media-row__meta" style="justify-content:space-between;width:100%">' +
			"<span>" +
			MN.fmt.bytes(dl.downloadedBytes) +
			" / " +
			MN.fmt.bytes(dl.fileSizeBytes) +
			"</span>" +
			'<span class="mn-num">' +
			pct +
			"%</span>" +
			"</div>";
		if (dl.status === "DOWNLOADING") {
			var meta = [];
			if (dl.speedBytesPerSec) meta.push(MN.fmt.speed(dl.speedBytesPerSec));
			if (dl.remainingMs > 0)
				meta.push(MN.fmt.duration(dl.remainingMs / 1000) + " left");
			if (meta.length) {
				html +=
					'<div class="mn-media-row__meta">' + MN.esc(meta.join(" · ")) + "</div>";
			}
		}
		return html;
	}

	function actionsFor(dl) {
		var st = MN.store.get();
		var isCurrent = st.playing && st.isLocal && st.playing.id === dl.videoId;
		var out = "";
		var s = dl.status;

		if (s === "QUEUED" || s === "DOWNLOADING") {
			out += actBtn("pause", "pause", "Pause", "mn-btn--secondary", dl.id);
			out += actBtn("cancel", "close", "Cancel", "mn-btn--ghost", dl.id);
		} else if (s === "PAUSED") {
			out += actBtn("resume", "play", "Resume", "mn-btn--primary", dl.id);
			out += actBtn("restart", "refresh", "Restart", "mn-btn--secondary", dl.id);
			out += actBtn("delete", "trash", "Delete", "mn-btn--ghost", dl.id);
		} else if (s === "FAILED") {
			out += actBtn("retry", "refresh", "Retry", "mn-btn--primary", dl.id);
			out += actBtn("delete", "trash", "Delete", "mn-btn--ghost", dl.id);
		} else if (s === "CANCELED") {
			out += actBtn("restart", "refresh", "Restart", "mn-btn--primary", dl.id);
			out += actBtn("delete", "trash", "Delete", "mn-btn--ghost", dl.id);
		} else if (s === "COMPLETED") {
			if (isCurrent) {
				out += actBtn(
					"toggle",
					st.isPlaying ? "pause" : "play",
					st.isPlaying ? "Pause" : "Play",
					"mn-btn--primary",
					dl.id,
				);
			} else {
				out += actBtn("play", "play", "Play", "mn-btn--primary", dl.id);
			}
			if (!isAudioDownload(dl)) {
				out += actBtn(
					"extract",
					"music",
					"Extract Audio",
					"mn-btn--secondary",
					dl.id,
				);
			}
			out += actBtn("delete", "trash", "Delete", "mn-btn--ghost", dl.id);
		}
		return out;
	}

	function downloadCard(dl) {
		var v = findVideo(dl.videoId);
		var dur = v ? v.durationSeconds : 0;
		var showStrip =
			dl.status === "DOWNLOADING" ||
			dl.status === "PAUSED" ||
			dl.status === "FAILED";

		var html =
			'<div class="mn-card mn-card--raised mn-dl-card" style="padding:12px;margin-bottom:12px">';
		html += '<div class="mn-row" style="align-items:flex-start;gap:12px">';

		html += '<div class="mn-thumb mn-dl-card__thumb" style="width:120px">';
		html += '<img src="' + MN.esc(dl.thumbnailUrl) + '" alt=""/>';
		html += typeBadgeFor(dl);
		html += statusBadge(dl);
		if (dur > 0) {
			html +=
				'<span class="mn-thumb__badge mn-thumb__badge--br">' +
				MN.fmt.duration(dur) +
				"</span>";
		}
		if (showStrip) {
			html +=
				'<div class="mn-thumb__progress ' +
				getProgressClass(dl) +
				'"><span style="width:' +
				Math.round((dl.progress || 0) * 100) +
				'%"></span></div>';
		}
		html += "</div>";

		html +=
			'<div class="mn-stack mn-fill mn-dl-card__body" style="gap:4px;min-width:0">';
		html += '<p class="mn-media-row__title">' + MN.esc(dl.title) + "</p>";
		html += '<div class="mn-media-row__meta">' + MN.esc(dl.quality) + "</div>";

		if (dl.status === "QUEUED") {
			html += '<div class="mn-media-row__meta">Waiting for a download slot…</div>';
		} else if (dl.status === "DOWNLOADING" || dl.status === "PAUSED") {
			html += progressBlock(dl);
		} else if (dl.status === "FAILED") {
			html +=
				'<div class="mn-error" style="font-size:var(--mn-fs-meta)">' +
				MN.esc(dl.errorMessage || "Download failed") +
				"</div>";
			html += progressBlock(dl);
		}

		html +=
			'<div class="mn-row mn-dl-card__actions" style="justify-content:flex-end;gap:2px;margin-top:4px">' +
			actionsFor(dl) +
			"</div>";
		html += "</div>";
		html += "</div>";
		html += "</div>";
		return html;
	}

	function toolbarHtml() {
		var max = MN.store.get().maxConcurrent;
		return (
			'<div class="mn-row mn-wrap" style="gap:8px;margin-bottom:16px">' +
			'<button class="mn-btn mn-btn--secondary" style="padding:0 14px" data-act="max" title="Set max concurrent downloads">' +
			MN.icon("sliders") +
			"<span>Max: " +
			max +
			"</span></button>" +
			'<button class="mn-btn mn-btn--ghost" style="padding:0 14px" data-act="pause-all" title="Pause all downloads">' +
			MN.icon("pause") +
			"<span>Pause All</span></button>" +
			'<button class="mn-btn mn-btn--ghost" style="padding:0 14px" data-act="resume-all" title="Resume all downloads">' +
			MN.icon("play") +
			"<span>Resume All</span></button>" +
			'<button class="mn-btn mn-btn--danger" style="padding:0 14px" data-act="delete-all" title="Delete all downloads">' +
			MN.icon("trash") +
			"<span>Delete All</span></button>" +
			'<button class="mn-btn mn-btn--secondary" style="padding:0 14px" data-act="sort" title="Sort download list">' +
			MN.icon(sortIconName()) +
			"<span>" +
			MN.esc(sortLabel()) +
			"</span></button>" +
			"</div>"
		);
	}

	function resumeHtml(cand) {
		var v = findVideo(cand.dl.videoId);
		var totalMs = (v ? v.durationSeconds : 0) * 1000;
		var frac = totalMs > 0 ? Math.min(1, cand.pos / totalMs) : 0;
		return (
			'<button class="mn-card mn-card--interactive" data-act="resume-play" title="Resume watching" style="width:100%;text-align:left;margin-bottom:16px">' +
			'<div class="mn-row" style="gap:12px;padding:12px">' +
			'<div class="mn-thumb" style="width:168px">' +
			'<img src="' +
			MN.esc(cand.dl.thumbnailUrl) +
			'" alt=""/>' +
			typeBadgeFor(cand.dl) +
			'<div class="mn-thumb__play" style="opacity:1">' +
			MN.icon("play") +
			"</div>" +
			'<div class="mn-thumb__progress"><span style="width:' +
			frac * 100 +
			'%"></span></div>' +
			"</div>" +
			'<div class="mn-stack mn-fill" style="gap:4px;min-width:0">' +
			'<p class="mn-key">Resume Watching</p>' +
			'<p class="mn-media-row__title">' +
			MN.esc(cand.dl.title) +
			"</p>" +
			'<p class="mn-media-row__meta">Left off at ' +
			MN.fmt.duration(cand.pos / 1000) +
			"</p>" +
			"</div>" +
			"</div>" +
			"</button>"
		);
	}

	function html() {
		var sorted = sortDownloads(DATA.downloads, sortCat, sortDir);
		var cand = resumeCandidate();
		var out = toolbarHtml() + statsHtml();
		if (cand) out += resumeHtml(cand);
		if (sorted.length === 0) {
			out += MN.stateView(
				"download",
				"No downloads yet",
				"Downloads will appear here. Extract a video or playlist from the Home tab.",
			);
		} else {
			out +=
				'<div class="mn-section-title"><h2>Downloads</h2><span class="mn-section-title__action">' +
				sorted.length +
				" items</span></div>";
			var visibleList = sorted.slice(0, visibleCount);
			visibleList.forEach((dl) => {
				out += downloadCard(dl);
			});
			if (visibleCount >= sorted.length) {
				out +=
					'<div class="mn-row mn-center mn-muted" style="padding:16px 0 8px;font-size:12px;letter-spacing:0.3px"><span style="opacity:0.6">• You have reached the end of the list •</span></div>';
			} else {
				out +=
					'<div class="mn-row mn-center mn-muted" style="padding:12px 0;font-size:12px">Loading…</div>';
			}
		}
		return out;
	}

	function render() {
		if (!viewEl) return;
		MN.render(viewEl, html());
	}

	function ensureTickBound() {
		if (tickBound) return;
		tickBound = true;
		MN.downloads.onTick(() => {
			render();
		});
	}

	function onAction(e) {
		var btn = e.target.closest("[data-act]");
		if (!btn) return;
		var act = btn.getAttribute("data-act");
		var id = btn.getAttribute("data-id");
		var dl = id ? findDownload(parseInt(id, 10)) : null;

		if (act === "max") openMaxSheet();
		else if (act === "sort") openSortSheet();
		else if (act === "status-toast") {
			var msg = btn.getAttribute("data-msg") || "Status info";
			MN.toast(msg, "info");
		} else if (act === "pause-all") pauseAll();
		else if (act === "resume-all") resumeAll();
		else if (act === "delete-all") confirmDeleteAll();
		else if (act === "resume-play") {
			var cand = resumeCandidate();
			if (cand) playDownload(cand.dl);
		} else if (act === "play" && dl) playDownload(dl);
		else if (act === "toggle") {
			MN.playback.toggle();
			render();
		} else if (act === "pause" && dl) {
			MN.downloads.pause(dl);
			render();
		} else if (act === "resume" && dl) {
			MN.downloads.resume(dl);
			render();
		} else if (act === "retry" && dl) {
			MN.downloads.retry(dl);
			render();
		} else if (act === "restart" && dl) confirmRestart(dl);
		else if (act === "cancel" && dl) confirmCancel(dl);
		else if (act === "delete" && dl) confirmDelete(dl);
		else if (act === "extract" && dl) extractAudio(dl);
	}

	function onScroll() {
		var root = viewEl || document.getElementById("view-root");
		if (!root) return;
		var sorted = sortDownloads(DATA.downloads, sortCat, sortDir);
		if (visibleCount >= sorted.length) return;
		if (root.scrollHeight - root.scrollTop - root.clientHeight < 80) {
			visibleCount += 10;
			render();
		}
	}

	MN.router.register("downloads", {
		title: "Downloads",
		back: false,
		mount: (el) => {
			viewEl = el;
			visibleCount = 10;
			el.onclick = onAction;
			el.addEventListener("scroll", onScroll);
			ensureTickBound();
			render();
			return {
				unmount: () => {
					el.removeEventListener("scroll", onScroll);
					viewEl = null;
				},
			};
		},
	});
})();

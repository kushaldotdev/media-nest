/* ============================================================================
   MediaNest — Statistics screen
   Registers route 'statistics' (linked from Settings).
   Thorough, feature-rich usage analytics for the library.
   ========================================================================== */

(() => {
	var MN = window.MN;
	var DATA = window.MN_DATA;

	var WEEK_MS = 7 * 24 * 60 * 60 * 1000;

	/* ---- helpers ----------------------------------------------------------- */

	function fmtWatch(seconds) {
		return MN.fmt.duration(seconds);
	}

	function isAudio(v) {
		return MN.isAudioTrack(v);
	}

	function fmtBytes(b) {
		return MN.fmt.bytes(b);
	}

	function now() {
		return Date.now();
	}

	/* ---- computations ------------------------------------------------------ */

	function computeStats() {
		var videos = DATA.videos || [];
		var downloads = DATA.downloads || [];
		var history = DATA.history || [];
		var sessions = DATA.watchSessions || [];
		var folders = DATA.folders || [];
		var subs = DATA.subscriptions || [];
		var linkHistory = DATA.linkHistory || [];

		/* 1. Library & Ratios */
		var totalTracked = videos.length;
		var audioTrackCount = videos.filter((v) => isAudio(v)).length;
		var videoTrackCount = totalTracked - audioTrackCount;
		var videoRatioPct =
			totalTracked > 0 ? Math.round((videoTrackCount / totalTracked) * 100) : 0;
		var audioRatioPct =
			totalTracked > 0 ? Math.round((audioTrackCount / totalTracked) * 100) : 0;

		/* 2. Completion */
		var watchedVideos = videos.filter((v) => (v.watchCount || 0) > 0).length;
		var completionPct =
			totalTracked > 0 ? Math.round((watchedVideos / totalTracked) * 100) : 0;

		/* 3. General engagement */
		var totalPlayCount = videos.reduce((sum, v) => sum + (v.watchCount || 0), 0);
		var favorites = videos.filter((v) => v.favorite).length;

		/* 4. Watch time */
		var totalWatchTimeMs = history.reduce(
			(sum, h) => sum + (h.totalWatchTimeMillis || 0),
			0,
		);
		if (totalWatchTimeMs === 0) {
			totalWatchTimeMs = videos.reduce(
				(sum, v) =>
					sum + (v.watchCount || 0) * (v.durationSeconds || 0) * 1000 * 0.7,
				0,
			);
		}

		var longestSessionMs = 0;
		history.forEach((h) => {
			if ((h.totalWatchTimeMillis || 0) > longestSessionMs) {
				longestSessionMs = h.totalWatchTimeMillis || 0;
			}
		});

		var avgSessionMs = history.length > 0 ? totalWatchTimeMs / history.length : 0;

		var cutoff = now() - WEEK_MS;
		var sessionsThisWeek = sessions.filter(
			(s) => (s.watchedAt || 0) >= cutoff,
		).length;
		var weekWatchMs = history
			.filter((h) => (h.playedAt || 0) >= cutoff)
			.reduce((sum, h) => sum + (h.totalWatchTimeMillis || 0), 0);

		/* 5. Downloads & Storage Details */
		var totalDownloadsCount = downloads.length;
		var completedDownloads = downloads.filter(
			(d) => d.status === "COMPLETED",
		).length;
		var activeDownloads = downloads.filter(
			(d) =>
				d.status === "DOWNLOADING" ||
				d.status === "QUEUED" ||
				d.status === "PAUSED",
		).length;
		var failedDownloads = downloads.filter((d) => d.status === "FAILED").length;
		var canceledDownloads = downloads.filter(
			(d) => d.status === "CANCELED",
		).length;

		var totalFinished = completedDownloads + failedDownloads + canceledDownloads;
		var downloadSuccessRate = 100;
		if (totalFinished > 0) {
			downloadSuccessRate = Math.round((completedDownloads / totalFinished) * 100);
		} else if (totalDownloadsCount > 0) {
			downloadSuccessRate = Math.round(
				(completedDownloads / totalDownloadsCount) * 100,
			);
		}

		var totalDownloadBytes = downloads.reduce(
			(sum, d) => sum + (d.fileSizeBytes || d.size || 0),
			0,
		);
		var completedBytes = downloads
			.filter((d) => d.status === "COMPLETED")
			.reduce((sum, d) => sum + (d.fileSizeBytes || d.size || 0), 0);
		var audioDownloads = downloads.filter((d) => isAudio(d));
		var audioBytes = audioDownloads.reduce(
			(sum, d) => sum + (d.fileSizeBytes || d.size || 0),
			0,
		);
		var videoBytes = totalDownloadBytes - audioBytes;
		var avgFileSize =
			totalDownloadsCount > 0
				? Math.round(totalDownloadBytes / totalDownloadsCount)
				: 0;

		var audioExtractionCount =
			downloads.filter((d) => d.format === "audio_extracted").length +
			videos.filter((v) => v.format === "audio_extracted").length;

		/* 6. Link Extraction Stats */
		var totalExtractedLinks = linkHistory.length;
		var videoLinksCount = 0;
		var playlistLinksCount = 0;
		var channelLinksCount = 0;
		var unknownLinksCount = 0;

		linkHistory.forEach((item) => {
			var kind = MN.linkKind ? MN.linkKind(item.url) : "unknown";
			if (kind === "video") videoLinksCount++;
			else if (kind === "playlist") playlistLinksCount++;
			else if (kind === "channel") channelLinksCount++;
			else unknownLinksCount++;
		});

		/* 7. Subscriptions & Folders */
		var subCount = subs.length;
		var autoDownloadCount = subs.filter((s) => s.autoDownload).length;
		var totalFolders = folders.length;

		/* 8. Content breakdowns */
		var resMap = {};
		videos.forEach((v) => {
			var r = v.resolution || "Unknown";
			resMap[r] = (resMap[r] || 0) + 1;
		});

		var channelMap = {};
		videos.forEach((v) => {
			var c = v.channelName || "Unknown";
			channelMap[c] = (channelMap[c] || 0) + 1;
		});

		var folderMap = {};
		folders.forEach((f) => {
			folderMap[f.id] = { name: f.name, count: 0 };
		});
		Object.keys(DATA.videoFolderMap || {}).forEach((vid) => {
			(DATA.videoFolderMap[vid] || []).forEach((f) => {
				if (folderMap[f.id]) folderMap[f.id].count += 1;
			});
		});

		/* 9. Top content */
		var top = videos
			.filter((v) => (v.watchCount || 0) > 0)
			.slice()
			.sort((a, b) => (b.watchCount || 0) - (a.watchCount || 0))
			.slice(0, 5);

		return {
			totalTracked: totalTracked,
			audioTrackCount: audioTrackCount,
			videoTrackCount: videoTrackCount,
			videoRatioPct: videoRatioPct,
			audioRatioPct: audioRatioPct,
			watchedVideos: watchedVideos,
			completionPct: completionPct,
			totalPlayCount: totalPlayCount,
			favorites: favorites,
			totalWatchSeconds: Math.round(totalWatchTimeMs / 1000),
			longestSessionSeconds: Math.round(longestSessionMs / 1000),
			avgSessionSeconds: Math.round(avgSessionMs / 1000),
			sessionsThisWeek: sessionsThisWeek,
			weekWatchSeconds: Math.round(weekWatchMs / 1000),
			totalDownloadsCount: totalDownloadsCount,
			completedDownloads: completedDownloads,
			activeDownloads: activeDownloads,
			failedDownloads: failedDownloads,
			canceledDownloads: canceledDownloads,
			downloadSuccessRate: downloadSuccessRate,
			totalDownloadBytes: totalDownloadBytes,
			completedBytes: completedBytes,
			audioBytes: audioBytes,
			videoBytes: videoBytes,
			avgFileSize: avgFileSize,
			audioExtractionCount: audioExtractionCount,
			totalExtractedLinks: totalExtractedLinks,
			videoLinksCount: videoLinksCount,
			playlistLinksCount: playlistLinksCount,
			channelLinksCount: channelLinksCount,
			unknownLinksCount: unknownLinksCount,
			subCount: subCount,
			autoDownloadCount: autoDownloadCount,
			totalFolders: totalFolders,
			resMap: resMap,
			channelMap: channelMap,
			folderMap: folderMap,
			top: top,
		};
	}

	/* ---- render helpers ---------------------------------------------------- */

	function statCard(iconName, label, value, sub) {
		return (
			'<div class="mn-stat"><div class="mn-stat__label">' +
			MN.icon(iconName, "mn-icon--sm") +
			" <span>" +
			MN.esc(label) +
			'</span></div><span class="mn-stat__value">' +
			MN.esc(value) +
			"</span>" +
			(sub ? '<span class="mn-stat__sub">' + MN.esc(sub) + "</span>" : "") +
			"</div>"
		);
	}

	function sectionTitle(iconName, title, statLine) {
		return (
			'<div class="mn-section-title">' +
			'<h2 style="display:inline-flex;align-items:center;gap:8px">' +
			'<span style="color:var(--mn-accent);display:inline-flex">' +
			MN.icon(iconName, "mn-icon--sm") +
			"</span>" +
			MN.esc(title) +
			"</h2>" +
			(statLine
				? '<span class="mn-section-title__action">' + MN.esc(statLine) + "</span>"
				: "") +
			"</div>"
		);
	}

	function barRow(label, count, total, cls) {
		var pct = total > 0 ? Math.round((count / total) * 100) : 0;
		var c = cls || "";
		return (
			'<div style="margin:8px 0">' +
			'<div class="mn-row mn-between" style="margin-bottom:4px">' +
			'<span class="mn-muted" style="font-size:var(--mn-fs-meta)">' +
			MN.esc(label) +
			"</span>" +
			'<span class="mn-num" style="font-size:var(--mn-fs-meta)">' +
			count +
			" · " +
			pct +
			"%</span>" +
			"</div>" +
			'<div class="mn-progress' +
			c +
			'"><span style="width:' +
			pct +
			'%"></span></div>' +
			"</div>"
		);
	}

	function contentRow(label, count) {
		return (
			'<div class="mn-row mn-between" style="padding:8px 0">' +
			'<span class="mn-muted" style="font-size:var(--mn-fs-meta)">' +
			MN.esc(label) +
			"</span>" +
			'<span class="mn-num" style="font-size:var(--mn-fs-meta)">' +
			count +
			"</span>" +
			"</div>"
		);
	}

	function topItemHtml(v, rank) {
		var timeSpentSec = (v.watchCount || 0) * (v.durationSeconds || 0);
		var frac =
			v.durationSeconds > 0
				? Math.min(1, timeSpentSec / (v.durationSeconds * 10))
				: 0;
		var pct = Math.round(frac * 100);
		return (
			'<div class="mn-card mn-card--raised" style="padding:10px;margin-bottom:10px">' +
			'<div class="mn-row" style="gap:12px">' +
			'<div class="mn-thumb" style="width:96px">' +
			'<img src="' +
			MN.esc(v.thumbnailUrl) +
			'" alt=""/>' +
			MN.typeBadge(v) +
			'<div class="mn-thumb__progress"><span style="width:' +
			pct +
			'%"></span></div>' +
			"</div>" +
			'<div class="mn-fill" style="min-width:0">' +
			'<p class="mn-key" style="margin:0">#' +
			rank +
			"</p>" +
			'<p class="mn-media-row__title" style="margin:2px 0 4px">' +
			MN.esc(v.title) +
			"</p>" +
			'<div class="mn-media-row__meta">' +
			MN.esc(v.channelName) +
			"</div>" +
			'<div class="mn-row mn-gap-2" style="margin-top:6px">' +
			'<span class="mn-tag mn-tag--accent">' +
			MN.icon("eye", "mn-icon--sm") +
			" " +
			(v.watchCount || 0) +
			"</span>" +
			'<span class="mn-tag">' +
			MN.icon("history", "mn-icon--sm") +
			" " +
			fmtWatch(timeSpentSec) +
			"</span>" +
			"</div>" +
			"</div>" +
			"</div>" +
			"</div>"
		);
	}

	function resRow(label, count, total) {
		return barRow(label, count, total, "");
	}

	/* ---- mount ------------------------------------------------------------- */

	function mount(el) {
		var s = computeStats();

		var html = "";

		/* Header */
		html +=
			'<div class="mn-row mn-gap-2" style="margin-bottom:16px">' +
			'<span style="display:inline-flex;width:32px;height:32px;color:var(--mn-accent)">' +
			MN.icon("chart", "mn-icon--lg") +
			"</span>" +
			'<div class="mn-fill">' +
			'<h2 style="margin:0;font-size:var(--mn-fs-title);font-weight:var(--mn-fw-bold)">App Statistics</h2>' +
			'<p class="mn-muted" style="margin:2px 0 0;font-size:var(--mn-fs-meta)">Library usage at a glance</p>' +
			"</div>" +
			"</div>";

		/* Overall engagement & library overview */
		html += sectionTitle(
			"chart",
			"Overall Engagement",
			s.totalTracked + " tracked videos",
		);
		html += '<div class="mn-stats-grid">';
		html += statCard(
			"video",
			"Tracked Videos",
			String(s.totalTracked),
			"library items",
		);
		html += statCard(
			"music",
			"Library Ratio",
			s.videoRatioPct + "% / " + s.audioRatioPct + "%",
			s.videoTrackCount + " video · " + s.audioTrackCount + " audio",
		);
		html += statCard(
			"check-circle",
			"Library Completion",
			s.completionPct + "%",
			s.watchedVideos + " of " + s.totalTracked + " watched",
		);
		html += statCard("play", "Total Plays", String(s.totalPlayCount), "sessions");
		html += statCard(
			"heart",
			"Favorites",
			String(s.favorites),
			"favorited videos",
		);
		html += statCard(
			"bell",
			"Subscriptions",
			String(s.subCount),
			s.autoDownloadCount + " auto-syncing",
		);
		html += "</div>";

		/* Watch metrics */
		html += sectionTitle(
			"history",
			"Engagement & Watch Metrics",
			s.sessionsThisWeek + " sessions this week",
		);
		html += '<div class="mn-stats-grid">';
		html += statCard(
			"history",
			"Total Watch Time",
			fmtWatch(s.totalWatchSeconds),
			"cumulative watch time",
		);
		html += statCard(
			"history",
			"Watch This Week",
			fmtWatch(s.weekWatchSeconds),
			s.sessionsThisWeek + " sessions",
		);
		html += statCard(
			"play",
			"Average Session",
			fmtWatch(s.avgSessionSeconds),
			"per playback session",
		);
		html += statCard(
			"star",
			"Longest Session",
			fmtWatch(s.longestSessionSeconds),
			"single longest session",
		);
		html += "</div>";

		/* Storage */
		html += sectionTitle(
			"download",
			"Storage Details",
			fmtBytes(s.totalDownloadBytes) + " on disk",
		);
		html += '<div class="mn-stats-grid" style="margin-bottom:12px">';
		html += statCard(
			"download",
			"Total Storage",
			fmtBytes(s.totalDownloadBytes),
			s.totalDownloadsCount + " downloads",
		);
		html += statCard(
			"video",
			"Video Storage",
			fmtBytes(s.videoBytes),
			"video files",
		);
		html += statCard(
			"music",
			"Audio Storage",
			fmtBytes(s.audioBytes),
			"audio files",
		);
		html += statCard(
			"file",
			"Average File Size",
			fmtBytes(s.avgFileSize),
			"per download",
		);
		html += statCard(
			"extract",
			"Audio Extractions",
			String(s.audioExtractionCount),
			"extracted tracks",
		);
		html += "</div>";
		html += '<div class="mn-card mn-card--pad">';
		html += barRow("Video Storage", s.videoBytes, s.totalDownloadBytes, "");
		html += barRow("Audio Storage", s.audioBytes, s.totalDownloadBytes, "");
		html += contentRow("Completed on disk", fmtBytes(s.completedBytes));
		html += contentRow(
			"Total download footprint",
			fmtBytes(s.totalDownloadBytes),
		);
		html += "</div>";

		/* Download stats */
		html += sectionTitle(
			"check-circle",
			"Download Health",
			s.downloadSuccessRate + "% success rate",
		);
		html += '<div class="mn-stats-grid">';
		html += statCard(
			"check-circle",
			"Success Rate",
			s.downloadSuccessRate + "%",
			s.completedDownloads + " completed",
		);
		html += statCard(
			"download",
			"Completed",
			String(s.completedDownloads),
			fmtBytes(s.completedBytes),
		);
		html += statCard(
			"pause",
			"Active / Queued",
			String(s.activeDownloads),
			"in progress",
		);
		html += statCard(
			"warning",
			"Failed / Canceled",
			String(s.failedDownloads + s.canceledDownloads),
			"unsuccessful",
		);
		html += "</div>";

		/* Link extraction stats */
		html += sectionTitle(
			"extract",
			"Link Extraction",
			s.totalExtractedLinks + " total extracted links",
		);
		html += '<div class="mn-stats-grid">';
		html += statCard(
			"extract",
			"Extracted Links",
			String(s.totalExtractedLinks),
			"in link history",
		);
		html += statCard(
			"youtube",
			"Video Links",
			String(s.videoLinksCount),
			"single videos",
		);
		html += statCard(
			"playlist",
			"Playlist Links",
			String(s.playlistLinksCount),
			"playlists",
		);
		html += statCard(
			"channel",
			"Channel Links",
			String(s.channelLinksCount),
			"channels",
		);
		html += "</div>";

		/* Subscriptions & Folders */
		html += sectionTitle(
			"folder",
			"Subscriptions & Folders",
			s.subCount + " subs · " + s.totalFolders + " folders",
		);
		html += '<div class="mn-stats-grid">';
		html += statCard(
			"bell",
			"Subscriptions",
			String(s.subCount),
			"channels & playlists",
		);
		html += statCard(
			"refresh",
			"Auto-Downloads",
			String(s.autoDownloadCount),
			"auto-sync enabled",
		);
		html += statCard(
			"folder",
			"Total Folders",
			String(s.totalFolders),
			"organized folders",
		);
		html += "</div>";

		/* Top content */
		html += sectionTitle("star", "Top Content", s.top.length + " most played");
		if (s.top.length === 0) {
			html += MN.stateView(
				"star",
				"No plays yet",
				"Play some videos to populate your top content.",
			);
		} else {
			s.top.forEach((v, i) => {
				html += topItemHtml(v, i + 1);
			});
		}

		/* Content breakdown: resolution */
		html += sectionTitle(
			"video",
			"By Resolution",
			Object.keys(s.resMap).length + " qualities",
		);
		html += '<div class="mn-card mn-card--pad">';
		Object.keys(s.resMap)
			.sort()
			.forEach((r) => {
				html += resRow(r, s.resMap[r], s.totalTracked);
			});
		html += "</div>";

		/* Content breakdown: channels */
		html += sectionTitle(
			"channel",
			"By Channel",
			Object.keys(s.channelMap).length + " channels",
		);
		html += '<div class="mn-card mn-card--pad">';
		Object.keys(s.channelMap)
			.sort()
			.forEach((c) => {
				html += contentRow(c, s.channelMap[c]);
			});
		html += "</div>";

		/* Content breakdown: folders */
		html += sectionTitle(
			"folder",
			"By Folder",
			Object.keys(s.folderMap).length + " folders",
		);
		html += '<div class="mn-card mn-card--pad">';
		Object.keys(s.folderMap)
			.sort((a, b) => s.folderMap[b].count - s.folderMap[a].count)
			.forEach((id) => {
				var f = s.folderMap[id];
				html += contentRow(f.name, f.count);
			});
		html += "</div>";

		/* Simulated note */
		html +=
			'<div class="mn-row mn-center mn-gap-2 mn-muted" style="margin:24px 0 16px;font-size:var(--mn-fs-mini)"><span style="display:inline-flex;color:var(--mn-text-secondary);flex:0 0 auto">' +
			MN.icon("info", "mn-icon--sm") +
			"</span><span>Data is simulated from the prototype mock library.</span></div>";

		MN.render(el, html);

		return {
			unmount: () => {},
		};
	}

	MN.router.register("statistics", {
		title: "App Statistics",
		back: true,
		mount: mount,
	});
})();

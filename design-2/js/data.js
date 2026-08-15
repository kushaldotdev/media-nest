/* ============================================================================
   MediaNest — Mock data
   Mirrors the real app's data model (VideoEntity, DownloadEntity, FolderEntity,
   SubscriptionEntity, HistoryEntity, WatchSessionEntity, LinkHistoryEntity).
   All thumbnail imagery is generated SVG gradients (media imagery, not chrome).
   ========================================================================== */

(() => {
	var DAY = 86400000;
	var HOUR = 3600000;
	var now = Date.now();

	/* --- SVG thumbnail generator (media imagery only, not UI chrome) --------- */
	function thumb(seed, label) {
		var gradients = [
			["#5a2a3a", "#8f1d2c"],
			["#3a2a5a", "#1d2c8f"],
			["#2a5a4a", "#1d8f6a"],
			["#5a4a2a", "#8f6a1d"],
			["#2a4a5a", "#1d6a8f"],
			["#4a2a2a", "#8f1d2c"],
			["#2a5a2a", "#3a8f1d"],
			["#2a2a4a", "#4a1d8f"],
		];
		var pair = gradients[seed % gradients.length];
		var title = label || "MediaNest";
		var parts = [];
		parts.push(
			'<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360">',
		);
		parts.push('<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">');
		parts.push('<stop offset="0" stop-color="' + pair[0] + '"/>');
		parts.push('<stop offset="1" stop-color="' + pair[1] + '"/>');
		parts.push("</linearGradient></defs>");
		parts.push('<rect width="640" height="360" fill="url(#g)"/>');
		parts.push(
			'<circle cx="' +
				(120 + (seed % 400)) +
				'" cy="' +
				(90 + (seed % 180)) +
				'" r="' +
				(80 + (seed % 60)) +
				'" fill="rgba(255,255,255,0.08)"/>',
		);
		parts.push(
			'<circle cx="' +
				(320 + (seed % 200)) +
				'" cy="' +
				(200 + (seed % 100)) +
				'" r="' +
				(50 + (seed % 70)) +
				'" fill="rgba(255,255,255,0.05)"/>',
		);
		parts.push(
			'<text x="40" y="300" font-family="Inter,Roboto,sans-serif" font-size="34" font-weight="700" opacity="0.95">' +
				title +
				"</text>",
		);
		parts.push(
			'<text x="40" y="330" font-family="Inter,Roboto,sans-serif" font-size="18" opacity="0.6">media imagery</text>',
		);
		parts.push("</svg>");
		return (
			"data:image/svg+xml;charset=utf-8," + encodeURIComponent(parts.join(""))
		);
	}

	function avatar(seed, label) {
		var pairs = [
			["#8f1d2c", "#3a0b12"],
			["#1d6a8f", "#0b213a"],
			["#6a1d8f", "#210b3a"],
			["#8f6a1d", "#3a210b"],
		];
		var pair = pairs[seed % pairs.length];
		var letter = String(label || "?")
			.charAt(0)
			.toUpperCase();
		var parts = [];
		parts.push(
			'<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200">',
		);
		parts.push('<defs><linearGradient id="a" x1="0" y1="0" x2="1" y2="1">');
		parts.push('<stop offset="0" stop-color="' + pair[0] + '"/>');
		parts.push('<stop offset="1" stop-color="' + pair[1] + '"/>');
		parts.push("</linearGradient></defs>");
		parts.push('<circle cx="100" cy="100" r="100" fill="url(#a)"/>');
		parts.push(
			'<text x="100" y="118" font-family="Inter,Roboto,sans-serif" font-size="72" font-weight="700" fill="rgba(255,255,255,0.92)" text-anchor="middle">' +
				letter +
				"</text>",
		);
		parts.push("</svg>");
		return (
			"data:image/svg+xml;charset=utf-8," + encodeURIComponent(parts.join(""))
		);
	}

	/* --- Helpers ------------------------------------------------------------- */
	function d(daysAgo, h) {
		h = h || 0;
		return now - daysAgo * DAY - h * HOUR;
	}

	/* --- Videos (library + search results) ----------------------------------- */
	var videoSeeds = [
		{
			t: "The Art of Analog Synthesizers",
			ch: "Synth Atlas",
			dur: 1462,
			seed: 1,
			date: "3d ago",
			res: "1080p",
			fav: true,
			watch: 3,
		},
		{
			t: "How I Built a Tiny House in the Forest",
			ch: "Off-Grid Living",
			dur: 1540,
			seed: 2,
			date: "1w ago",
			res: "1080p",
			fav: false,
			watch: 1,
		},
		{
			t: "Cinematic Drone Tour: Norwegian Fjords",
			ch: "Wander Films",
			dur: 512,
			seed: 3,
			date: "2w ago",
			res: "4K",
			fav: true,
			watch: 2,
		},
		{
			t: "Neo-Soul Guitar Chords Explained",
			ch: "Fretboard Theory",
			dur: 934,
			seed: 4,
			date: "4d ago",
			res: "720p",
			fav: false,
			watch: 0,
		},
		{
			t: "Deep Focus: Lo-Fi Coding Mix",
			ch: "Chill Beats Lab",
			dur: 3600,
			seed: 5,
			date: "5d ago",
			res: "Audio",
			fav: true,
			watch: 6,
		},
		{
			t: "Making Perfect Sourdough at Home",
			ch: "Bake & Bloom",
			dur: 1280,
			seed: 6,
			date: "1mo ago",
			res: "1080p",
			fav: false,
			watch: 1,
		},
		{
			t: "The Physics of Black Holes",
			ch: "Deep Sky Science",
			dur: 1980,
			seed: 7,
			date: "3w ago",
			res: "1440p",
			fav: false,
			watch: 2,
		},
		{
			t: "Street Photography in Tokyo at Night",
			ch: "Lens & Light",
			dur: 745,
			seed: 8,
			date: "2d ago",
			res: "1080p",
			fav: true,
			watch: 4,
		},
		{
			t: "Minimalist Desk Setup 2026",
			ch: "Workspace Daily",
			dur: 1120,
			seed: 1,
			date: "6d ago",
			res: "1440p",
			fav: false,
			watch: 0,
		},
		{
			t: "A Walk Through Kyoto Gardens",
			ch: "Wander Films",
			dur: 640,
			seed: 2,
			date: "5mo ago",
			res: "720p",
			fav: false,
			watch: 1,
		},
		{
			t: "Jazz Piano Improvisation Masterclass",
			ch: "Keys & Harmony",
			dur: 2750,
			seed: 3,
			date: "2mo ago",
			res: "1080p",
			fav: true,
			watch: 3,
		},
		{
			t: "Espresso Extraction Fundamentals",
			ch: "Coffee Craft",
			dur: 980,
			seed: 4,
			date: "1d ago",
			res: "1080p",
			fav: false,
			watch: 0,
		},
		{
			t: "Morning Yoga Flow for Beginners",
			ch: "Mindful Movement",
			dur: 1300,
			seed: 5,
			date: "8d ago",
			res: "720p",
			fav: false,
			watch: 2,
		},
		{
			t: "Understanding Rust Ownership",
			ch: "Code Pathways",
			dur: 1660,
			seed: 6,
			date: "3d ago",
			res: "1080p",
			fav: true,
			watch: 1,
		},
		{
			t: "How Vinyl Records Are Made",
			ch: "Analog Archive",
			dur: 890,
			seed: 7,
			date: "1mo ago",
			res: "1080p",
			fav: false,
			watch: 0,
		},
	];

	var videos = videoSeeds.map((v, i) => ({
		id: "vid_" + (1000 + i),
		title: v.t,
		channelName: v.ch,
		channelId: "ch_" + v.seed,
		durationSeconds: v.dur,
		thumbnailUrl: thumb(v.seed, v.t.slice(0, 18)),
		description:
			"A deeper look into " +
			v.t.toLowerCase() +
			". Filmed and edited for a relaxed, immersive viewing experience with clear narration and beautiful visuals.",
		uploadDate: v.date,
		localFilePath:
			i % 3 === 0
				? "/storage/emulated/0/MediaNest/video/" + (1000 + i) + ".mp4"
				: "",
		favorite: v.fav,
		addedAt: d(i + 2),
		lastPlayedAt: v.watch > 0 ? d(1) : null,
		downloadedAt: i % 3 === 0 ? d(i + 1) : null,
		watchCount: v.watch,
		publicViews: v.seed * 180000 + i * 37211 + 125000,
		resolution: v.res,
	}));

	/* --- Playlist / channel search results (Home) ---------------------------- */
	var playlistVideos = videoSeeds.slice(0, 9).map((v, i) => ({
		id: "pv_" + (2000 + i),
		title: v.t,
		channelName: v.ch,
		durationSeconds: v.dur,
		thumbnailUrl: thumb(v.seed + 10, v.t.slice(0, 16)),
		uploadDate: v.date,
		isShort: i === 5,
	}));

	var playlist = {
		playlistId: "PL_demo_mix",
		name: "Late Night Focus Mix",
		thumbnailUrl: thumb(12, "Late Night Focus"),
		videoCount: 42,
		description:
			"A hand-picked selection of deep ambient, lo-fi beats, and relaxing instrumentals designed for late night study and focus sessions. Updated weekly with new tracks from top indie creators.",
		videos: playlistVideos,
	};

	var channel = {
		channelId: "ch_wander",
		name: "Wander Films",
		avatarUrl: avatar(2, "W"),
		url: "https://www.youtube.com/@wanderfilms",
		videoCount: 214,
		description:
			"Documenting breathtaking landscapes, culture, and outdoor adventures around the world in 4K resolution. Join us as we explore mountain ranges, coastal trails, and vibrant cities.",
		uploads: playlistVideos
			.slice()
			.reverse()
			.map((x) => x),
	};

	/* --- Subscriptions (channels + playlists) -------------------------------- */
	var subscriptions = [
		{
			id: 1,
			sourceType: "channel",
			sourceId: "ch_wander",
			name: "Wander Films",
			thumbnailUrl: avatar(2, "W"),
			autoDownload: true,
			audioOnly: false,
		},
		{
			id: 2,
			sourceType: "channel",
			sourceId: "ch_synth",
			name: "Synth Atlas",
			thumbnailUrl: avatar(1, "S"),
			autoDownload: false,
			audioOnly: false,
		},
		{
			id: 3,
			sourceType: "channel",
			sourceId: "ch_chill",
			name: "Chill Beats Lab",
			thumbnailUrl: avatar(4, "C"),
			autoDownload: true,
			audioOnly: true,
		},
		{
			id: 4,
			sourceType: "channel",
			sourceId: "ch_bake",
			name: "Bake & Bloom",
			thumbnailUrl: avatar(3, "B"),
			autoDownload: false,
			audioOnly: false,
		},
		{
			id: 5,
			sourceType: "playlist",
			sourceId: "PL_demo_mix",
			name: "Late Night Focus Mix",
			thumbnailUrl: thumb(12, "Focus"),
			autoDownload: false,
			audioOnly: false,
		},
		{
			id: 6,
			sourceType: "playlist",
			sourceId: "PL_demo_road",
			name: "Road Trip Anthems",
			thumbnailUrl: thumb(13, "Road Trip"),
			autoDownload: true,
			audioOnly: true,
		},
	];

	/* --- Folders ------------------------------------------------------------- */
	var folders = [
		{ id: 1, name: "Learning", parentId: null, createdAt: d(60) },
		{ id: 2, name: "Music & Ambience", parentId: null, createdAt: d(45) },
		{ id: 3, name: "Documentaries", parentId: null, createdAt: d(30) },
		{ id: 4, name: "Guitar", parentId: 1, createdAt: d(20) },
		{ id: 5, name: "Coffee", parentId: 1, createdAt: d(12) },
	];

	var videoFolderMap = {
		vid_1001: [folders[1]],
		vid_1003: [folders[2]],
		vid_1005: [folders[1], folders[0]],
		vid_1008: [folders[2]],
		vid_1011: [folders[0], folders[3]],
		vid_1013: [folders[0], folders[4]],
	};

	/* --- Downloads ----------------------------------------------------------- */
	var downloadSeeds = [
		{
			vid: 0,
			format: "video",
			quality: "1080p (vp9)",
			status: "DOWNLOADING",
			progress: 0.62,
			size: 214000000,
			downloaded: 132000000,
			speed: 4200000,
		},
		{
			vid: 1,
			format: "video",
			quality: "720p (h264)",
			status: "QUEUED",
			progress: 0,
			size: 88000000,
			downloaded: 0,
			speed: 0,
		},
		{
			vid: 2,
			format: "audio",
			quality: "128kbps (opus)",
			status: "COMPLETED",
			progress: 1,
			size: 6200000,
			downloaded: 6200000,
			speed: 0,
		},
		{
			vid: 3,
			format: "video",
			quality: "1080p (h264)",
			status: "PAUSED",
			progress: 0.38,
			size: 156000000,
			downloaded: 59000000,
			speed: 0,
		},
		{
			vid: 4,
			format: "video_only",
			quality: "4K (vp9)",
			status: "FAILED",
			progress: 0.11,
			size: 420000000,
			downloaded: 46000000,
			speed: 0,
			err: "Network error — stream expired",
		},
		{
			vid: 5,
			format: "audio_extracted",
			quality: "128kbps (m4a)",
			status: "COMPLETED",
			progress: 1,
			size: 5200000,
			downloaded: 5200000,
			speed: 0,
		},
	];

	var downloads = downloadSeeds.map((s, i) => {
		var v = videos[s.vid];
		return {
			id: i + 1,
			videoId: v.id,
			title: v.title,
			thumbnailUrl: v.thumbnailUrl,
			format: s.format,
			quality: s.quality,
			status: s.status,
			progress: s.progress,
			fileSizeBytes: s.size,
			downloadedBytes: s.downloaded,
			speedBytesPerSec: s.speed,
			elapsedMs: 34000 + i * 12000,
			remainingMs:
				s.status === "DOWNLOADING"
					? Math.round((s.size - s.downloaded) / (s.speed || 1)) * 1000
					: 0,
			filePath:
				"/storage/emulated/0/MediaNest/video/" +
				v.id +
				(s.format.includes("audio") ? ".m4a" : ".mp4"),
			downloadedAt: d(i),
			errorMessage:
				s.err ||
				(s.status === "DOWNLOADING"
					? "downloading_video|132000000|214000000|0|4200000|34000|28000"
					: null),
		};
	});

	/* --- History ------------------------------------------------------------- */
	var history = videos
		.filter((v) => v.watchCount > 0)
		.map((v, i) => ({
			videoId: v.id,
			positionMillis: Math.round(
				v.durationSeconds * 1000 * (0.25 + (i % 5) * 0.14),
			),
			playedAt: d(i + 1),
			totalWatchTimeMillis: v.watchCount * v.durationSeconds * 1000 * 0.7,
		}));

	var watchSessions = [
		{ videoId: "vid_1005", watchedAt: d(0, 2) },
		{ videoId: "vid_1005", watchedAt: d(2, 1) },
		{ videoId: "vid_1001", watchedAt: d(1, 3) },
	];

	/* --- Link history -------------------------------------------------------- */
	var linkHistory = [
		{
			url: "https://www.youtube.com/watch?v=abc123",
			title: "The Art of Analog Synthesizers",
			extractedAt: d(1),
		},
		{
			url: "https://www.youtube.com/playlist?list=PL_demo_mix",
			title: "Late Night Focus Mix",
			extractedAt: d(2),
		},
		{
			url: "https://www.youtube.com/@wanderfilms",
			title: "Wander Films — Channel",
			extractedAt: d(3),
		},
		{
			url: "https://www.youtube.com/watch?v=xyz789",
			title: "How I Built a Tiny House in the Forest",
			extractedAt: d(4),
		},
	];

	/* --- Notifications (existing + proposed) --------------------------------- */
	var notifications = [
		{
			id: 1,
			type: "success",
			title: "Download complete",
			desc: "Neo-Soul Guitar Chords Explained is ready to play.",
			time: d(0, 0.5),
			channel: "Downloads",
		},
		{
			id: 2,
			type: "info",
			title: "New uploads available",
			desc: "Chill Beats Lab published 3 new videos.",
			time: d(0, 3),
			channel: "Subscriptions",
		},
		{
			id: 3,
			type: "info",
			title: "Auto-download queued",
			desc: "Road Trip Anthems: 2 new tracks added to queue.",
			time: d(1),
			channel: "Subscriptions",
		},
		{
			id: 4,
			type: "error",
			title: "Download failed",
			desc: "Cinematic Drone Tour — network error.",
			time: d(1, 2),
			channel: "Downloads",
		},
		{
			id: 5,
			type: "success",
			title: "Sync complete",
			desc: "History & subscriptions synced across devices.",
			time: d(2),
			channel: "VPS Sync",
		},
		{
			id: 6,
			type: "success",
			title: "Download complete",
			desc: "The Art of Analog Synthesizers is ready to play.",
			time: d(2, 4),
			channel: "Downloads",
		},
		{
			id: 7,
			type: "info",
			title: "New uploads available",
			desc: "Off-Grid Living published 1 new video.",
			time: d(3),
			channel: "Subscriptions",
		},
		{
			id: 8,
			type: "info",
			title: "Auto-backup complete",
			desc: "Library metadata backup saved successfully.",
			time: d(3, 6),
			channel: "System",
		},
		{
			id: 9,
			type: "success",
			title: "Audio extracted",
			desc: "Deep Focus: Lo-Fi Coding Mix audio track ready.",
			time: d(4),
			channel: "Downloads",
		},
		{
			id: 10,
			type: "error",
			title: "Sync error",
			desc: "VPS connection timed out. Retrying in 1 hour.",
			time: d(4, 8),
			channel: "VPS Sync",
		},
		{
			id: 11,
			type: "info",
			title: "New channel subscription",
			desc: "Subscribed to Fretboard Theory auto-downloads.",
			time: d(5),
			channel: "Subscriptions",
		},
		{
			id: 12,
			type: "success",
			title: "Download complete",
			desc: "Making Perfect Sourdough at Home is ready to play.",
			time: d(5, 5),
			channel: "Downloads",
		},
		{
			id: 13,
			type: "info",
			title: "Storage notification",
			desc: "Used storage reaches 14.2 GB of available media space.",
			time: d(6),
			channel: "System",
		},
		{
			id: 14,
			type: "info",
			title: "Auto-download queued",
			desc: "Jazz Piano Improvisation Masterclass added to queue.",
			time: d(6, 10),
			channel: "Subscriptions",
		},
		{
			id: 15,
			type: "success",
			title: "Sync complete",
			desc: "Play histories and favorites synced to server.",
			time: d(7),
			channel: "VPS Sync",
		},
		{
			id: 16,
			type: "success",
			title: "Download complete",
			desc: "Street Photography in Tokyo at Night ready.",
			time: d(7, 12),
			channel: "Downloads",
		},
		{
			id: 17,
			type: "error",
			title: "Download failed",
			desc: "The Physics of Black Holes — storage space low.",
			time: d(8),
			channel: "Downloads",
		},
		{
			id: 18,
			type: "info",
			title: "New uploads available",
			desc: "Wander Films published 2 new videos.",
			time: d(8, 6),
			channel: "Subscriptions",
		},
		{
			id: 19,
			type: "info",
			title: "App update available",
			desc: "MediaNest v1.1.0 is available for update.",
			time: d(9),
			channel: "System",
		},
		{
			id: 20,
			type: "success",
			title: "Bulk download complete",
			desc: "Late Night Focus Mix (9 videos) downloaded.",
			time: d(9, 14),
			channel: "Downloads",
		},
		{
			id: 21,
			type: "success",
			title: "Audio extracted",
			desc: "Minimalist Desk Setup 2026 audio saved.",
			time: d(10),
			channel: "Downloads",
		},
		{
			id: 22,
			type: "info",
			title: "Folder updated",
			desc: "Moved 3 items to Learning folder.",
			time: d(10, 4),
			channel: "System",
		},
		{
			id: 23,
			type: "info",
			title: "Auto-download queued",
			desc: "Understanding Rust Ownership added to queue.",
			time: d(11),
			channel: "Subscriptions",
		},
		{
			id: 24,
			type: "success",
			title: "Sync complete",
			desc: "Device registered and synced with remote VPS.",
			time: d(11, 8),
			channel: "VPS Sync",
		},
		{
			id: 25,
			type: "error",
			title: "Extraction error",
			desc: "Failed to parse stream URL for private video.",
			time: d(12),
			channel: "Downloads",
		},
		{
			id: 26,
			type: "success",
			title: "Download complete",
			desc: "Espresso Extraction Fundamentals ready to play.",
			time: d(12, 16),
			channel: "Downloads",
		},
		{
			id: 27,
			type: "info",
			title: "New uploads available",
			desc: "Code Pathways published 1 new tutorial.",
			time: d(13),
			channel: "Subscriptions",
		},
		{
			id: 28,
			type: "info",
			title: "Auto-backup complete",
			desc: "Weekly full database backup created.",
			time: d(14),
			channel: "System",
		},
		{
			id: 29,
			type: "success",
			title: "Download complete",
			desc: "Morning Yoga Flow for Beginners ready.",
			time: d(14, 6),
			channel: "Downloads",
		},
		{
			id: 30,
			type: "info",
			title: "Queue updated",
			desc: "Reordered 4 items in Now Playing queue.",
			time: d(15),
			channel: "System",
		},
		{
			id: 31,
			type: "success",
			title: "Audio extracted",
			desc: "How Vinyl Records Are Made audio track extracted.",
			time: d(15, 12),
			channel: "Downloads",
		},
		{
			id: 32,
			type: "error",
			title: "Sync error",
			desc: "API key invalid or expired.",
			time: d(16),
			channel: "VPS Sync",
		},
		{
			id: 33,
			type: "info",
			title: "New channel subscription",
			desc: "Subscribed to Analog Archive.",
			time: d(17),
			channel: "Subscriptions",
		},
		{
			id: 34,
			type: "success",
			title: "Download complete",
			desc: "A Walk Through Kyoto Gardens ready in 720p.",
			time: d(18),
			channel: "Downloads",
		},
		{
			id: 35,
			type: "info",
			title: "Library repair complete",
			desc: "Scanned and verified 15 media files on disk.",
			time: d(19),
			channel: "System",
		},
	];

	window.MN_DATA = {
		videos: videos,
		playlistVideos: playlistVideos,
		playlist: playlist,
		channel: channel,
		subscriptions: subscriptions,
		folders: folders,
		videoFolderMap: videoFolderMap,
		downloads: downloads,
		history: history,
		watchSessions: watchSessions,
		linkHistory: linkHistory,
		notifications: notifications,
		helpers: {
			thumb: thumb,
			avatar: avatar,
			DAY: DAY,
			HOUR: HOUR,
			now: now,
			d: d,
		},
	};
})();

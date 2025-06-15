package com.frever.ml.utils;

import static com.frever.ml.utils.RandomUtils.getRandom;

import com.frever.ml.comfy.dto.*;
import com.frever.ml.dto.CandidateVideo;
import com.frever.ml.dto.GeoCluster;
import com.frever.ml.dto.RecommendedVideo;
import com.frever.ml.dto.VideoInfo;
import com.google.common.io.Resources;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ConfigUtils;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Utils {
    private static final String DEFAULT_POSITIVE_PROMPT_TEXT = """
        (realism, best quality) realistic characters, male character, cinematic, animation, detailed clothes, detailed face, octane rendering, strong keylight, 8k, high resolution, masterpiece, trending on artstation""";

    private static final String DEFAULT_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String ANIME_POSITIVE_PROMPT_TEXT = """
        (anime 2d animation), anime, 2d character, cinematic, cartoon, 2d animation, 2d animation, detailed face, outline, cell shading, 8k, high resolution, masterpiece, pastel, trending on artstation""";

    private static final String ANIME_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String CARTOON_POSITIVE_PROMPT_TEXT = """
        (pixar 3d animation), disney character, cinematic, pixar animation, 3d animation, detailed face, octane rendering, strong keylight, 8k, high resolution, masterpiece, trending on artstation""";

    private static final String CARTOON_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String REALISM_POSITIVE_PROMPT_TEXT = """
        (realism, best quality) realistic characters, male character, cinematic, animation, detailed clothes, detailed face, octane rendering, strong keylight, 8k, high resolution, masterpiece, trending on artstation""";

    private static final String REALISM_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String VFI_POSITIVE_PROMPT_TEXT = """
        (pixar 3d animation), disney character, cinematic, pixar animation, 3d animation, detailed face, octane rendering, strong keylight, 8k, high resolution, masterpiece, trending on artstation""";

    private static final String VFI_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String CYBERPUNK_POSITIVE_PROMPT_TEXT = """
        (cyberpunk, best quality) cyberpunk characters, neon trails, futuristic, high-speed action, dynamic style, cinematic, animation, detailed clothes, detailed face, octane rendering, strong keylight, 8k, high resolution, masterpiece, trending on artstation""";

    private static final String CYBERPUNK_NEGATIVE_PROMPT_TEXT = """
        text, error, blurry, cropped, nsfw, (signature), watermark, username, artist name, worst quality, bad anatomy, (blurred), (out of focus), green hair, forehead wrinkle, cross eyed, creepy""";

    private static final String PHOTO_PULID_PROMPT_TEXT = """
        woman, pigtails blonde hair, golden dress accentuating her fit body, long dress, sheer dress, diamond jewelery necklace, plunging neckline, high cut, eyes closed, hands touching head, standing in front of eiffel tower in paris, high heels, sunset, vivid colors, extremely intricate details, masterpiece, epic, clear shadows and highlights, realistic, intense, enhanced contrast, highly detailed skin.""";

    private static final String PHOTO_PULID_2_PROMPT_TEXT = """
        In front of Great Pyramid in Egypt, two women are captured in close-up shot. 2 women is visible in the camera frame. The woman are very happy and smiling. They are dressed in casual summer clothes, with striking elegant hair, embodying a romantic aesthetic. Birds fills the background. The atmosphere is sunset. The scene is highly detailed and cinematic, presented in 4K high resolution, creating an immersive experience.""";

    private static final String PHOTO_PULID_3_PROMPT_TEXT = """
        In front of Great Pyramid in Egypt, 3 women are captured in close-up shot. 3 women is visible in the camera frame. They are very happy and smiling. They are dressed in casual summer clothes, with striking elegant hair, embodying a romantic aesthetic. Birds fills the background. The atmosphere is sunset. The scene is highly detailed and cinematic, presented in 4K high resolution, creating an immersive experience.""";

    private static final String FLUX_PHOTO_PROMPT_PROMPT_TEXT = """
        A lifestyle influencer in santorini greece. Ethereal, dreamy, deeply spiritual vibes. Aesthetic: Flowing fabrics, soft pastels, ocean waves, celestial elements. Brand Collaborations: Dior, Chloé, Loewe, Cartier. She is Calm, nurturing, introspective, speaks in poetic phrases.""";

    private static final String AUDIO_DEFAULT_PROMPT_TEXT = """
        Hello everyone, and welcome! In these brief ten seconds, allow me to introduce you to the charm of technological innovation. The future is here – join us as we explore endless possibilities!""";

    private static final String MUSIC_GEN_DEFAULT_PROMPT_TEXT = """
        Classical music""";

    private static final String MM_AUDIO_DEFAULT_PROMPT_TEXT = """
        underwater bubbles""";

    private static final String ACE_PLUS_DEFAULT_PROMPT_TEXT = """
        black dress with red roses""";

    public static final Map<String, Prompts> WORKFLOW_TO_PROMPTS = Map.ofEntries(
        Map.entry("anime", new Prompts(ANIME_POSITIVE_PROMPT_TEXT, ANIME_NEGATIVE_PROMPT_TEXT)),
        Map.entry("cartoon", new Prompts(CARTOON_POSITIVE_PROMPT_TEXT, CARTOON_NEGATIVE_PROMPT_TEXT)),
        Map.entry("default", new Prompts(DEFAULT_POSITIVE_PROMPT_TEXT, DEFAULT_NEGATIVE_PROMPT_TEXT)),
        Map.entry("realism", new Prompts(REALISM_POSITIVE_PROMPT_TEXT, REALISM_NEGATIVE_PROMPT_TEXT)),
        Map.entry("vfi", new Prompts(VFI_POSITIVE_PROMPT_TEXT, VFI_NEGATIVE_PROMPT_TEXT)),
        Map.entry("cyberpunk", new Prompts(CYBERPUNK_POSITIVE_PROMPT_TEXT, CYBERPUNK_NEGATIVE_PROMPT_TEXT)),
        Map.entry("phone-face-swap", new Prompts(REALISM_POSITIVE_PROMPT_TEXT, REALISM_NEGATIVE_PROMPT_TEXT))
    );

    private static final Map<String, Integer> WORKFLOW_PROCESSING_PER_SECOND_DURATION_SECONDS = Map.ofEntries(
        Map.entry(ComfyUiWorkflow.VIDEO_LATENT_SYNC_WORKFLOW.getWorkflowName(), 80),
        Map.entry(ComfyUiWorkflow.SONIC_TEXT_WORKFLOW.getWorkflowName(), 240),
        Map.entry(ComfyUiWorkflow.SONIC_AUDIO_WORKFLOW.getWorkflowName(), 400)
    );

    private static final int MAX_SEED = Integer.MAX_VALUE;
    private static final int DEFAULT_PROCESSING_TIME = 200;
    public static final int DEFAULT_WAITING_TIME_SECONDS = 60;
    public static final int MAX_DURATION_FOR_LATENT_SYNC = 10;
    public static final int COMFY_PORT = 8188;

    private Utils() {
    }

    public static String replaceNewLinesWithSpace(String text) {
        return text.replaceAll("[\\t\\n\\r]+", " ").replaceAll("\"", "");
    }

    public static String readFileFromClasspath(String path) {
        try {
            return Resources.toString(Resources.getResource(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warnf(e, "Failed to find %s using Resource.", path);
        }
        return null;
    }

    public static String generateComfyUiPrompt(
        String workflow,
        String json,
        String video,
        String positivePromptText,
        String negativePromptText,
        String clientId
    ) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        if (Objects.nonNull(video)) {
            temp = temp.replace("___video___", video);
            if (video.contains(".")) {
                temp = temp.replace("___outputPrefix___", video.substring(0, video.lastIndexOf(".")));
            } else {
                temp = temp.replace("___outputPrefix___", video);
            }
        }
        var prompts = WORKFLOW_TO_PROMPTS.get(workflow);
        if (Objects.isNull(positivePromptText) || positivePromptText.isBlank()) {
            positivePromptText = prompts.positivePrompt();
        }
        temp = temp.replace("___positivePromptText___", positivePromptText);
        if (Objects.isNull(negativePromptText) || negativePromptText.isBlank()) {
            negativePromptText = prompts.negativePrompt();
        }
        temp = temp.replace("___negativePromptText___", negativePromptText);
        Log.infof(
            "video: %s, positivePromptText: %s, negativePromptText: %s",
            video,
            positivePromptText,
            negativePromptText
        );
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiLatentSync(
        String json,
        String[] videoAndAudio,
        String clientId,
        int startTime,
        int duration
    ) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        temp = temp.replace("___startTime___", Integer.toString(startTime));
        if (duration == 0) {
            duration = MAX_DURATION_FOR_LATENT_SYNC;
        } else {
            duration = Math.min(duration, MAX_DURATION_FOR_LATENT_SYNC);
        }
        temp = temp.replace("___duration___", Integer.toString(duration));
        var video = videoAndAudio[0];
        var audio = videoAndAudio[1];
        temp = temp.replace("___video___", video).replace("___audio___", audio);
        if (video.contains(".")) {
            temp = temp.replace("___outputPrefix___", video.substring(0, video.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", video);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiRequestWithInputAndPromptTextAndSeed(
        String json,
        String input,
        String promptText,
        String clientId
    ) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        return generateComfyUiRequestWithInputAndPromptText(temp, input, promptText, clientId);
    }

    public static String generateComfyUiRequestWithInputAndPromptTextAndTwoContextValuesAndSeed(
        String json,
        String input,
        String promptText,
        ContextSwitchSpeakerMode speakerMode,
        ContextSwitchLanguageMode languageMode,
        String clientId
    ) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        return generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
            temp,
            input,
            promptText,
            speakerMode,
            languageMode,
            clientId
        );
    }

    public static String generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
        String json,
        String input,
        String promptText,
        ContextSwitchSpeakerMode speakerMode,
        ContextSwitchLanguageMode languageMode,
        String clientId
    ) {
        if (promptText == null || promptText.isEmpty()) {
            promptText = AUDIO_DEFAULT_PROMPT_TEXT;
        }
        return generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
            json,
            input,
            promptText,
            speakerMode.getContextValue(),
            languageMode.getContextValue(),
            clientId
        );
    }

    private static String generateComfyUiRequestWithInputAndPromptTextAndTwoContextValues(
        String json,
        String input,
        String promptText,
        int contextValue1,
        int contextValue2,
        String clientId
    ) {
        var result = generateComfyUiRequestWithInputAndPromptText(json, input, promptText, clientId);
        return result.replace("___contextValue___", String.valueOf(contextValue1))
            .replace("___contextValue2___", String.valueOf(contextValue2));
    }

    public static String generateComfyUiRequestWithInputAndPromptText(
        String json,
        String input,
        String promptText,
        String clientId
    ) {
        String temp = json.replace("___input___", input);
        if (promptText == null || promptText.isBlank()) {
            promptText = AUDIO_DEFAULT_PROMPT_TEXT;
        }
        temp = temp.replace("___promptText___", replaceNewLinesWithSpace(promptText));
        if (input.contains(".")) {
            temp = temp.replace("___outputPrefix___", input.substring(0, input.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", input);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiRequestWithInputAndAudio(
        String json,
        List<String> inputs,
        String clientId
    ) {
        return generateComfyUiRequestWithInputAndAudio(json, inputs, clientId, false, 0, 0);
    }

    public static String generateComfyUiRequestWithInputAndAudio(
        String json,
        List<String> inputs,
        String clientId,
        int startTime,
        int duration
    ) {
        return generateComfyUiRequestWithInputAndAudio(json, inputs, clientId, false, startTime, duration);
    }

    public static String generateComfyUiRequestWithInputAndAudio(
        String json,
        List<String> inputs,
        String clientId,
        boolean hasSeed,
        int audioStartTime,
        int audioDuration
    ) {
        var input = inputs.getFirst();
        var audio = inputs.get(1);
        String temp = json.replace("___input___", input);
        temp = temp.replace("___audio___", audio);
        if (input.contains(".")) {
            temp = temp.replace("___outputPrefix___", input.substring(0, input.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", input);
        }
        if (hasSeed) {
            long seed = getRandom().nextLong(0L, MAX_SEED);
            temp = temp.replace("___seed___", String.valueOf(seed));
        }
        temp = temp.replace("___startTime___", String.valueOf(audioStartTime));
        int duration = audioDuration == 0 ? 15 : Math.min(audioDuration, 15);
        temp = temp.replace("___duration___", String.valueOf(duration));
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiFaceSwap(String json, String[] videoAndImage, String clientId) {
        var video = videoAndImage[0];
        var image = videoAndImage[1];
        var temp = json.replace("___video___", video);
        temp = temp.replace("___image___", image);
        if (video.contains(".")) {
            temp = temp.replace("___outputPrefix___", video.substring(0, video.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", video);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generatePlainComfyUiRequest(String json, String clientId) {
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, json
        );
    }

    private static String generateComfyUiPhotoRequest(String json, String image, String promptText, String clientId) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        if (image != null && !image.isBlank()) {
            temp = temp.replace("___image___", image);
        }
        if (image != null && image.contains(".")) {
            temp = temp.replace("___outputPrefix___", image.substring(0, image.lastIndexOf(".")));
        } else if (image != null) {
            temp = temp.replace("___outputPrefix___", image);
        }
        temp = temp.replace("___promptText___", replaceNewLinesWithSpace(promptText));
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    private static String generateComfyUiTwoPhotosRequest(
        String json,
        String input,
        String source,
        String promptText,
        String clientId
    ) {
        var result = generateComfyUiPhotoRequest(json, input, promptText, clientId);
        return result.replace("___source___", source);
    }

    public static String generateComfyUiPhotoPulid(String json, String image, String promptText, String clientId) {
        if (promptText == null || promptText.isBlank()) {
            promptText = PHOTO_PULID_PROMPT_TEXT;
        }
        return generateComfyUiPhotoRequest(json, image, promptText, clientId);
    }

    public static String generateComfyUiPhotoPulidMultiChars(
        String json,
        String[] images,
        String promptText,
        String clientId
    ) {
        if (promptText == null || promptText.isBlank()) {
            if (images.length == 2) {
                promptText = PHOTO_PULID_2_PROMPT_TEXT;
            } else if (images.length == 3) {
                promptText = PHOTO_PULID_3_PROMPT_TEXT;
            } else {
                throw new IllegalArgumentException("We only support 2 or 3 images for photo pulid.");
            }
        }
        var result = generateComfyUiPhotoFaceSwap(json, images, clientId);
        long seed = getRandom().nextLong(0L, MAX_SEED);
        return result.replace("___promptText___", replaceNewLinesWithSpace(promptText))
            .replace("___seed___", String.valueOf(seed));
    }

    public static String generateComfyUiPhotoFlux(String json, String image, String promptText, String clientId) {
        if (promptText == null || promptText.isBlank()) {
            promptText = FLUX_PHOTO_PROMPT_PROMPT_TEXT;
        }
        return generateComfyUiPhotoRequest(json, image, promptText, clientId);
    }

    public static String generateComfyUiPhotoFluxReduxStyle(
        String json,
        String input,
        String source,
        String promptText,
        String clientId
    ) {
        if (promptText == null || promptText.isBlank()) {
            promptText = FLUX_PHOTO_PROMPT_PROMPT_TEXT;
        }
        return generateComfyUiTwoPhotosRequest(json, input, source, promptText, clientId);
    }

    public static String generateComfyUiPhotoRealism(String json, String video, String clientId) {
        long seed = getRandom().nextLong(0L, MAX_SEED);
        String temp = json.replace("___seed___", String.valueOf(seed));
        temp = temp.replace("___video___", video);
        if (video.contains(".")) {
            temp = temp.replace("___outputPrefix___", video.substring(0, video.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", video);
        }
        var prompts = WORKFLOW_TO_PROMPTS.get("realism");
        temp = temp.replace("___positivePromptText___", prompts.positivePrompt());
        temp = temp.replace("___negativePromptText___", prompts.negativePrompt());
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiPhotoAcePlus(
        String json,
        List<String> images,
        String promptText,
        String clientId,
        AcePlusWardrobeMode wardrobeMode,
        AcePlusReferenceMode referenceMode,
        AcePlusMaskMode maskMode
    ) {
        if (promptText == null || promptText.isBlank()) {
            promptText = ACE_PLUS_DEFAULT_PROMPT_TEXT;
        }
        String temp = generateInputAndOutputPrefix(json, images);
        if (referenceMode == AcePlusReferenceMode.UploadImage) {
            if (images.size() < 2) {
                throw new IllegalArgumentException("We need at least 2 images for ace plus.");
            }
            var source = images.get(1);
            temp = temp.replace("___source___", source);
        } else {
            temp = temp.replace("___source___", "not-exists");
        }
        if (maskMode == AcePlusMaskMode.ManualMask) {
            if (images.size() < 2) {
                throw new IllegalArgumentException("We need at least 2 images for ace plus.");
            }
            var mask = images.getLast();
            temp = temp.replace("___mask___", mask);
        } else {
            temp = temp.replace("___mask___", "not-exists");
        }
        long seed = getRandom().nextLong(0L, MAX_SEED);
        temp = temp.replace("___promptText___", replaceNewLinesWithSpace(promptText))
            .replace("___seed___", String.valueOf(seed))
            .replace("___contextValue___", String.valueOf(wardrobeMode.getContextValue()))
            .replace("___contextValue2___", String.valueOf(referenceMode.getContextValue()))
            .replace("___contextValue3___", String.valueOf(maskMode.getContextValue()));
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiVideoLivePortrait(
        String json,
        List<String> files,
        String clientId,
        int sourceAudioStartTime,
        int sourceAudioDuration,
        LivePortraitAudioInputMode audioInputMode,
        LivePortraitCopperMode copperMode,
        LivePortraitModelMode modelMode
    ) {
        String temp = generateInputAndOutputPrefix(json, files);
        if (audioInputMode == LivePortraitAudioInputMode.InputAudio) {
            if (files.size() < 2) {
                throw new IllegalArgumentException("At least 2 files for video-live-portrait InputAudio mode.");
            }
            var sourceAudio = files.get(1);
            temp = temp.replace("___sourceAudio___", sourceAudio);
        } else {
            temp = temp.replace("___sourceAudio___", "example-10-minutes-of-silence.mp3");
        }
        if (audioInputMode == LivePortraitAudioInputMode.InputVideoDrivingAudio) {
            if (files.size() < 2) {
                throw new IllegalArgumentException("At least 2 files for video-live-portrait InputVideoDrivingAudio.");
            }
            var sourceVideo = files.getLast();
            temp = temp.replace("___sourceVideo___", sourceVideo);
        } else {
            temp = temp.replace("___sourceVideo___", "example-video.mp4");
        }
        long seed = getRandom().nextLong(0L, MAX_SEED);
        int duration = sourceAudioDuration == 0 ? 15 : Math.min(sourceAudioDuration, 15);
        temp = temp.replace("___seed___", String.valueOf(seed))
            .replace("___contextValue___", String.valueOf(audioInputMode.getContextValue()))
            .replace("___contextValue2___", String.valueOf(copperMode.getContextValue()))
            .replace("___contextValue3___", String.valueOf(modelMode.getContextValue()))
            .replace("___startTime___", String.valueOf(sourceAudioStartTime))
            .replace("___duration___", String.valueOf(duration));
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    private static String generateInputAndOutputPrefix(String json, List<String> files) {
        var input = files.getFirst();
        String temp = json.replace("___input___", input);
        if (input.contains(".")) {
            temp = temp.replace("___outputPrefix___", input.substring(0, input.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", input);
        }
        return temp;
    }

    public static String generateComfyUiPhotoMakeup(String json, String[] images, String clientId) {
        if (images == null || images.length < 2) {
            throw new IllegalArgumentException("We need at least 2 images for makeup.");
        }
        var input = images[0];
        String temp = json.replace("___input___", input);
        var makeup = images[1];
        temp = temp.replace("___makeup___", makeup);
        if (input.contains(".")) {
            temp = temp.replace("___outputPrefix___", input.substring(0, input.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", input);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiPhotoFaceSwap(String json, String[] images, String clientId) {
        var input = images[0];
        String temp = json.replace("___input___", input);
        for (int i = 1; i < images.length; i++) {
            temp = temp.replace("___source" + i + "___", images[i]);
        }
        if (input.contains(".")) {
            temp = temp.replace("___outputPrefix___", input.substring(0, input.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", input);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiMultipleCharactersFaceSwap(
        String json,
        String[] videoAndImages,
        String clientId
    ) {
        if (videoAndImages.length != 3 && videoAndImages.length != 4) {
            throw new IllegalArgumentException("We only support  2 or 3 images for face swap.");
        }
        var video = videoAndImages[0];
        var temp = json.replace("___video___", video);
        for (int i = 1; i < videoAndImages.length; i++) {
            temp = temp.replace("___image" + i + "___", videoAndImages[i]);
        }
        if (video.contains(".")) {
            temp = temp.replace("___outputPrefix___", video.substring(0, video.lastIndexOf(".")));
        } else {
            temp = temp.replace("___outputPrefix___", video);
        }
        return String.format(
            """
                {
                    "client_id": "%s",
                    "prompt": %s
                }
                """, clientId, temp
        );
    }

    public static String generateComfyUiMusicGen(
        String json,
        String input,
        String promptText,
        MusicGenBackGroundMusic contextValue,
        String clientId
    ) {
        if (promptText == null || promptText.isEmpty()) {
            promptText = MUSIC_GEN_DEFAULT_PROMPT_TEXT;
        }
        var result = generateComfyUiRequestWithInputAndPromptTextAndSeed(json, input, promptText, clientId);
        return result.replace("___contextValue___", String.valueOf(contextValue.getContextValue()));
    }

    public static String generateComfyUiMmAudio(
        String json,
        String input,
        String promptText,
        MmAudioAudioMode audioMode,
        MmAudioPromptMode promptMode,
        String clientId
    ) {
        if (promptText == null || promptText.isEmpty()) {
            promptText = MM_AUDIO_DEFAULT_PROMPT_TEXT;
        }
        return generateComfyUiRequestWithInputAndPromptTextAndSeedAndTwoContextValues(
            json,
            input,
            promptText,
            audioMode.getContextValue(),
            promptMode.getContextValue(),
            clientId
        );
    }

    public static String generateComfyUiStillImageText(
        String json,
        String input,
        String promptText,
        ContextSwitchSpeakerMode speakerMode,
        ContextSwitchLanguageMode languageMode,
        String clientId
    ) {
        if (promptText == null || promptText.isEmpty()) {
            promptText = AUDIO_DEFAULT_PROMPT_TEXT;
        }
        return generateComfyUiRequestWithInputAndPromptTextAndSeedAndTwoContextValues(
            json,
            input,
            promptText,
            speakerMode.getContextValue(),
            languageMode.getContextValue(),
            clientId
        );
    }

    private static String generateComfyUiRequestWithInputAndPromptTextAndSeedAndTwoContextValues(
        String json,
        String input,
        String promptText,
        int contextValue1,
        int contextValue2,
        String clientId
    ) {
        var result = generateComfyUiRequestWithInputAndPromptTextAndSeed(json, input, promptText, clientId);
        return result.replace("___contextValue___", String.valueOf(contextValue1))
            .replace("___contextValue2___", String.valueOf(contextValue2));
    }

    public static List<RecommendedVideo> fromVideoInfoList(
        List<VideoInfo> videoInfo,
        GeoCluster geoCluster,
        int orderStart,
        String source
    ) {
        if (videoInfo.isEmpty()) {
            return List.of();
        }
        List<CandidateVideo> candidateVideos = videoInfo.stream()
            .map(v -> new CandidateVideo(v, geoCluster.priority()))
            .toList();
        var result = new ArrayList<RecommendedVideo>();
        int order = orderStart;
        for (var video : candidateVideos) {
            result.add(new RecommendedVideo(video, order++, source));
        }
        return result;
    }

    public record Prompts(String positivePrompt, String negativePrompt) {

    }

    public static String[] getDestinationS3BucketAndKey(String destinationBucketPath) {
        if (!destinationBucketPath.startsWith("s3://")) {
            Log.warnf("Invalid destinationBucketPath: %s", destinationBucketPath);
            throw new IllegalArgumentException("Invalid destinationBucketPath: " + destinationBucketPath);
        }
        String[] parts = destinationBucketPath.substring(5).split("/", 2);
        if (parts.length != 2) {
            Log.warnf("Invalid destinationBucketPath: %s", destinationBucketPath);
            throw new IllegalArgumentException("Invalid destinationBucketPath: " + destinationBucketPath);
        }
        return parts;
    }

    public static String normalizeS3Path(String s3Path) {
        if (s3Path.endsWith("/")) {
            return s3Path;
        }
        return s3Path + "/";
    }

    public static boolean isOnAWS() {
        boolean runOnEc2 = Files.isDirectory(Paths.get("/home/ec2-user"));
        if (runOnEc2) {
            return true;
        }
        String ecsContainerMetadataUriV4 = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
        if (ecsContainerMetadataUriV4 == null || ecsContainerMetadataUriV4.isEmpty()) {
            return false;
        }
        try {
            var connection = (HttpURLConnection) new URI(ecsContainerMetadataUriV4).toURL().openConnection();
            connection.setConnectTimeout(200);
            connection.setReadTimeout(200);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            Log.infof(
                e,
                "Failed to contact %s due to %s, assuming local env.",
                ecsContainerMetadataUriV4,
                e.getMessage()
            );
            return false;
        }
    }

    public static int taskDurationEstimationSeconds(
        UUID promptId,
        String workflow,
        int videoDurationSeconds,
        Instant startedAt
    ) {
        if (!WORKFLOW_PROCESSING_PER_SECOND_DURATION_SECONDS.containsKey(workflow)) {
            Log.warnf("Unknown workflow: %s", workflow);
            return videoDurationSeconds * DEFAULT_PROCESSING_TIME;
        }
        Instant now = Instant.now();
        int processingPerSecond = WORKFLOW_PROCESSING_PER_SECOND_DURATION_SECONDS.get(workflow);
        int wholeProcessingTimeEstimation = videoDurationSeconds * processingPerSecond;
        if (startedAt != null) {
            long passed = now.getEpochSecond() - startedAt.getEpochSecond();
            Log.infof("PromptId %s passed %d seconds", promptId, passed);
            if (passed > 0) {
                int currentEstimation = wholeProcessingTimeEstimation - (int) passed;
                if (currentEstimation > 0) {
                    return currentEstimation;
                } else {
                    return DEFAULT_WAITING_TIME_SECONDS;
                }
            } else {
                return wholeProcessingTimeEstimation;
            }
        } else {
            return wholeProcessingTimeEstimation;
        }
    }

    public static String normalizeServerIp(String serverIp) {
        if (serverIp.contains(":")) {
            var realIp = serverIp.split(":")[0];
            if (realIp.equalsIgnoreCase("localhost")) {
                Log.info("normalizeServerIp for localhost...");
                return "127.0.0.1";
            }
            return realIp;
        }
        return serverIp;
    }

    public static boolean isProd() {
        return ConfigUtils.getProfiles().contains("prod");
    }

    public static boolean isDev() {
        return ConfigUtils.getProfiles().contains("dev");
    }

    public static String getMultiCharWorkflowForPulid(String baseName, int numberOfImages) {
        if (numberOfImages == 1) {
            throw new IllegalArgumentException("photo-pulid is not supported anymore.");
        } else if (numberOfImages == 2) {
            return baseName + "-2";
        } else if (numberOfImages == 3) {
            return baseName + "-3";
        } else {
            Log.warnf("Unsupported number of images: %d", numberOfImages);
            return baseName;
        }
    }

    public static String addPortToServerIpIfNeeded(String serverIp) {
        if (serverIp.contains(":")) {
            return serverIp;
        }
        if (serverIp.equals("localhost") || serverIp.equals("127.0.0.1")) {
            Log.infof("Running on localhost...");
            return serverIp + ":" + 8188;
        }
        return serverIp + ":" + COMFY_PORT;
    }

    public static boolean invalidQueueUrl(String queueUrl) {
        return queueUrl == null || queueUrl.isBlank() || !queueUrl.startsWith("https://sqs.");
    }
}

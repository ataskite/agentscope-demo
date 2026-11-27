package com.skloda.agentscope.model;

import com.skloda.agentscope.security.UploadPathGuard;
import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures live inside the upload whitelist directory (P1-2): client-supplied media
 * paths are only honored inside {java.io.tmpdir}/agentscope-uploads.
 */
class MultiModalMessageTest {

    private final List<String> fixtures = new java.util.ArrayList<>();

    private Path whitelistFixture(String name, byte[] content) throws java.io.IOException {
        Path file = UploadPathGuard.uploadDir().resolve(name);
        Files.createDirectories(UploadPathGuard.uploadDir());
        Files.write(file, content);
        fixtures.add(name);
        return file;
    }

    @AfterEach
    void cleanup() throws java.io.IOException {
        for (String name : fixtures) {
            Files.deleteIfExists(UploadPathGuard.uploadDir().resolve(name));
        }
        fixtures.clear();
    }

    @Test
    void withImageCreatesTextAndImageBlocks() throws Exception {
        Path image = whitelistFixture("mm-sample.png", new byte[] {1, 2, 3});

        Msg msg = MultiModalMessage.withImage("look", image.toString(), "sample.png");

        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals(2, msg.getContent().size());
        TextBlock textBlock = assertInstanceOf(TextBlock.class, msg.getContent().get(0));
        ImageBlock imageBlock = assertInstanceOf(ImageBlock.class, msg.getContent().get(1));
        assertEquals("look", textBlock.getText());
        Base64Source source = assertInstanceOf(Base64Source.class, imageBlock.getSource());
        assertEquals("image/png", source.getMediaType());
    }

    @Test
    void withImageFallsBackToTextWhenFileCannotBeRead() {
        Path missing = UploadPathGuard.uploadDir().resolve("mm-missing-" + System.nanoTime() + ".jpg");

        Msg msg = MultiModalMessage.withImage("look", missing.toString(), "missing.jpg");

        assertEquals(MsgRole.USER, msg.getRole());
        assertTrue(msg.getTextContent().contains("[图片加载失败: missing.jpg]"));
    }

    @Test
    void withAudioCreatesAudioBlock() throws Exception {
        Path audio = whitelistFixture("mm-sample.mp3", new byte[] {4, 5, 6});

        Msg msg = MultiModalMessage.withAudio(audio.toString(), "sample.mp3");

        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals(1, msg.getContent().size());
        AudioBlock audioBlock = assertInstanceOf(AudioBlock.class, msg.getContent().get(0));
        Base64Source source = assertInstanceOf(Base64Source.class, audioBlock.getSource());
        assertEquals("audio/mp3", source.getMediaType());
    }

    @Test
    void withMultipleImagesCreatesAllImageBlocks() throws Exception {
        Path first = whitelistFixture("mm-first.jpg", new byte[] {1});
        Path second = whitelistFixture("mm-second.webp", new byte[] {2});

        Msg msg = MultiModalMessage.withMultipleImages("compare", List.of(
                new MultiModalMessage.ImageFile(first.toString(), "first.jpg"),
                new MultiModalMessage.ImageFile(second.toString(), "second.webp")
        ));

        assertEquals(3, msg.getContent().size());
        assertInstanceOf(TextBlock.class, msg.getContent().get(0));
        assertInstanceOf(ImageBlock.class, msg.getContent().get(1));
        assertInstanceOf(ImageBlock.class, msg.getContent().get(2));
    }
}

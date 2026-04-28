package com.skloda.agentscope.model;

import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiModalMessageTest {

    @TempDir
    private Path tempDir;

    @Test
    void withImageCreatesTextAndImageBlocks() throws Exception {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[] {1, 2, 3});

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
        Msg msg = MultiModalMessage.withImage("look", tempDir.resolve("missing.jpg").toString(), "missing.jpg");

        assertEquals(MsgRole.USER, msg.getRole());
        assertTrue(msg.getTextContent().contains("[图片加载失败: missing.jpg]"));
    }

    @Test
    void withAudioCreatesAudioBlock() throws Exception {
        Path audio = tempDir.resolve("sample.mp3");
        Files.write(audio, new byte[] {4, 5, 6});

        Msg msg = MultiModalMessage.withAudio(audio.toString(), "sample.mp3");

        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals(1, msg.getContent().size());
        AudioBlock audioBlock = assertInstanceOf(AudioBlock.class, msg.getContent().get(0));
        Base64Source source = assertInstanceOf(Base64Source.class, audioBlock.getSource());
        assertEquals("audio/mp3", source.getMediaType());
    }

    @Test
    void withMultipleImagesCreatesAllImageBlocks() throws Exception {
        Path first = tempDir.resolve("first.jpg");
        Path second = tempDir.resolve("second.webp");
        Files.write(first, new byte[] {1});
        Files.write(second, new byte[] {2});

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

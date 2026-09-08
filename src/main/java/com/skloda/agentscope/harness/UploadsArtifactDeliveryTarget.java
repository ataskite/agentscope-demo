package com.skloda.agentscope.harness;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryRequest;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryResult;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * agentscope 2.0.3 新增的 deliver_artifact 沙箱产物交付 SPI 的演示实现。
 * <p>
 * 通过 {@code HarnessAgent.Builder.artifactDeliveryTarget(...)} 挂载后，harness 会自动注册
 * {@code deliver_artifact} 工具并在 workspace 提示词中引用它，使 Docker 沙箱内的 agent
 * 能够把生成的文件交付到宿主机（沙箱销毁后产物不丢失）。
 * <p>
 * 本实现把交付内容写到 {@code {java.io.tmpdir}/agentscope-uploads/{uuid}{ext}}，
 * 与 {@code ChatController} 的 /chat/upload 落盘约定保持一致。规则：
 * <ul>
 *   <li>落盘成功 → {@code success("已交付: " + 绝对路径)}</li>
 *   <li>目标已存在且 force=false → conflict；force=true → 覆盖</li>
 *   <li>IO 异常 → fail</li>
 * </ul>
 * 注意：UUID 由 fileName（缺省时 filePath）确定性推导（{@link UUID#nameUUIDFromBytes}），
 * 因此同名产物多次交付会落到同一路径，从而让 conflict / force 覆盖语义真实可触发。
 */
public class UploadsArtifactDeliveryTarget implements ArtifactDeliveryTarget {

    private static final Logger log = LoggerFactory.getLogger(UploadsArtifactDeliveryTarget.class);

    /** 上传目录名，与 /chat/upload 约定一致 */
    static final String UPLOAD_DIR_NAME = "agentscope-uploads";

    @Override
    public ArtifactDeliveryResult deliver(RuntimeContext ctx, ArtifactDeliveryRequest request) {
        String fileName = request.fileName();
        String filePath = request.filePath();

        Path target = uploadsDir().resolve(resolveFileId(fileName, filePath) + resolveExtension(fileName, filePath));

        try {
            Files.createDirectories(uploadsDir());
            if (Files.exists(target) && !request.force()) {
                log.warn("Artifact delivery conflict: {} (force=false)", target.toAbsolutePath());
                return ArtifactDeliveryResult.conflict("目标文件已存在: " + target.toAbsolutePath()
                        + "（如需覆盖请携带 force=true 重新交付）");
            }
            byte[] content = request.content() != null ? request.content() : new byte[0];
            Files.write(target, content);
            log.info("Artifact delivered: {} ({} bytes, force={})", target.toAbsolutePath(), content.length, request.force());
            return ArtifactDeliveryResult.success("已交付: " + target.toAbsolutePath());
        } catch (IOException e) {
            log.error("Artifact delivery failed for {} -> {}", fileName, target.toAbsolutePath(), e);
            return ArtifactDeliveryResult.fail("交付失败: " + e.getMessage());
        }
    }

    /** 上传目录：{java.io.tmpdir}/agentscope-uploads */
    static Path uploadsDir() {
        return Paths.get(System.getProperty("java.io.tmpdir"), UPLOAD_DIR_NAME);
    }

    /**
     * 由 fileName（缺省时 filePath）确定性推导 UUID：同名产物多次交付得到同一路径，
     * 使 conflict / force 语义可触发；两者均缺失时退化为随机 UUID。
     */
    private UUID resolveFileId(String fileName, String filePath) {
        if (fileName != null && !fileName.isBlank()) {
            return UUID.nameUUIDFromBytes(fileName.trim().getBytes(StandardCharsets.UTF_8));
        }
        if (filePath != null && !filePath.isBlank()) {
            return UUID.nameUUIDFromBytes(filePath.trim().getBytes(StandardCharsets.UTF_8));
        }
        return UUID.randomUUID();
    }

    /** 从 fileName（缺省时 filePath）提取扩展名（含点，统一小写），无扩展名则返回空串 */
    private String resolveExtension(String fileName, String filePath) {
        String name = (fileName != null && !fileName.isBlank()) ? fileName : filePath;
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }
}

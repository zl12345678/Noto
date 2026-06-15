package com.noto.zhihui.drive;

import com.noto.zhihui.dto.note.NoteCreateRequest;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.service.AttachmentService;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.support.AbstractIntegrationTest;
import com.noto.zhihui.vo.note.NoteVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DriveAttachmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private NoteService noteService;

    @Test
    void listByWorkspaceWithAttachmentShouldNotThrow() throws Exception {
        registerAndGetToken();
        WorkspaceEntity workspace = workspaceService.lambdaQuery().last("LIMIT 1").one();
        Long userId = workspace.getOwnerUserId();
        Long workspaceId = workspace.getId();

        NoteCreateRequest noteRequest = new NoteCreateRequest();
        noteRequest.setTitle("附件测试");
        noteRequest.setContent("test");
        noteRequest.setWorkspaceId(workspaceId);
        NoteVO note = noteService.createNote(noteRequest, userId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "debug.txt",
                "text/plain",
                "hello".getBytes()
        );
        attachmentService.upload(note.getId(), file, userId);

        assertDoesNotThrow(() -> attachmentService.listByWorkspace(workspaceId, null, null, null, null, null, userId));
        assertDoesNotThrow(() -> attachmentService.listByWorkspace(workspaceId, null, true, null, null, null, userId));
        assertDoesNotThrow(() -> attachmentService.listByWorkspace(workspaceId, null, null, null, true, null, userId));
        assertDoesNotThrow(() -> attachmentService.listByNote(note.getId(), userId));
    }
}

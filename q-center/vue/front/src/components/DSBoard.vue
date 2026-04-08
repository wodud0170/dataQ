<template>
  <v-main>
    <!-- 검색 영역 -->
    <v-sheet class="pa-3" style="background:#fff; border-bottom:1px solid #E8EAF6;">
      <v-row align="center" no-gutters style="gap:8px;">
        <v-select v-model="searchField" :items="searchFieldOptions" item-text="label" item-value="value"
          dense outlined hide-details style="max-width:120px;" />
        <v-text-field v-model="keyword" dense outlined hide-details clearable placeholder="검색어 입력"
          style="max-width:250px;" @keyup.enter="loadList" />
        <v-btn class="gradient" @click="loadList" style="min-width:45px;">
          <v-icon>search</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
        <v-btn class="gradient" @click="showWriteForm"
          v-if="boardType === 'QNA' || isAdmin">
          <v-icon small class="mr-1">mdi-pencil</v-icon> 글쓰기
        </v-btn>
      </v-row>
    </v-sheet>

    <!-- 목록 모드 -->
    <template v-if="viewMode === 'list'">
      <v-data-table :headers="listHeaders" :items="boardList" :page.sync="page"
        :items-per-page="itemsPerPage" hide-default-footer item-key="boardId"
        class="px-4 pb-3" :loading="loading" @click:row="viewPost">
        <template v-slot:[`item.rowNum`]="{ item }">
          <v-icon v-if="item.pinYn === 'Y'" small color="red" class="mr-1">mdi-pin</v-icon>
          <span v-else>{{ item.rowNum }}</span>
        </template>
        <template v-slot:[`item.title`]="{ item }">
          <span class="ndColor--text" style="cursor:pointer;">{{ item.title }}</span>
          <v-icon v-if="item.fileCnt > 0" x-small color="grey" class="ml-1">mdi-paperclip</v-icon>
          <v-chip v-if="item.commentCnt > 0" x-small color="grey lighten-2" class="ml-1">[{{ item.commentCnt }}]</v-chip>
        </template>
        <template v-slot:no-data>
          <v-alert>게시글이 없습니다.</v-alert>
        </template>
      </v-data-table>

      <div class="text-center px-4 pt-2 pb-4" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </template>

    <!-- 상세 모드 -->
    <template v-if="viewMode === 'detail'">
      <v-sheet class="mx-4 mt-4 pa-4" outlined style="border-radius:8px;">
        <v-btn text small @click="goBack" class="mb-2">
          <v-icon small class="mr-1">mdi-arrow-left</v-icon> 목록으로
        </v-btn>
        <div class="text-h6 font-weight-bold">
          <v-icon v-if="currentPost.pinYn === 'Y'" small color="red" class="mr-1">mdi-pin</v-icon>
          {{ currentPost.title }}
        </div>
        <div class="grey--text text-caption mt-1">
          {{ currentPost.cretUserId }} | {{ currentPost.cretDt }}
          <span v-if="currentPost.updtDt"> | 수정: {{ currentPost.updtDt }}</span>
          | 조회 {{ currentPost.viewCnt }}
        </div>
        <v-divider class="my-3"></v-divider>
        <div class="post-content" style="min-height:200px; white-space:pre-wrap;">{{ currentPost.content }}</div>

        <!-- 첨부파일 -->
        <div v-if="attachFiles.length > 0" class="mt-3">
          <div class="text-caption font-weight-bold mb-1"><v-icon x-small class="mr-1">mdi-paperclip</v-icon>첨부파일</div>
          <div v-for="f in attachFiles" :key="f.fileId" class="d-flex align-center mb-1">
            <a :href="$APIURL.base + 'api/board/file/download/' + f.fileId" class="text-caption ndColor--text" style="text-decoration:none;">
              {{ f.fileNm }}
            </a>
            <span class="text-caption grey--text ml-2">({{ formatFileSize(f.fileSize) }})</span>
          </div>
        </div>

        <!-- 수정/삭제 -->
        <v-row v-if="currentPost.cretUserId === currentUserId || isAdmin" class="mt-3">
          <v-col cols="auto">
            <v-btn small outlined color="ndColor" @click="editPost">수정</v-btn>
          </v-col>
          <v-col cols="auto">
            <v-btn small outlined color="red" @click="deletePost">삭제</v-btn>
          </v-col>
        </v-row>

        <v-divider class="my-4"></v-divider>

        <!-- 댓글 -->
        <div class="text-subtitle-2 font-weight-bold mb-2">댓글 ({{ comments.length }})</div>
        <div v-for="c in comments" :key="c.commentId" class="comment-item pa-2 mb-2" style="background:#F5F7FA; border-radius:4px;">
          <div class="d-flex justify-space-between align-center">
            <span class="text-caption font-weight-bold">{{ c.cretUserId }}</span>
            <span class="text-caption grey--text">{{ c.cretDt }}</span>
          </div>
          <template v-if="editingCommentId === c.commentId">
            <v-textarea v-model="editCommentContent" rows="2" dense outlined hide-details color="ndColor" class="mt-1"></v-textarea>
            <div class="d-flex mt-1" style="gap:4px;">
              <v-btn x-small color="ndColor" dark @click="saveCommentEdit(c.commentId)">저장</v-btn>
              <v-btn x-small text @click="cancelCommentEdit">취소</v-btn>
            </div>
          </template>
          <template v-else>
            <div class="text-body-2 mt-1" style="white-space:pre-wrap;">{{ c.content }}</div>
            <div v-if="c.cretUserId === currentUserId || isAdmin" class="mt-1">
              <v-btn x-small text color="ndColor" @click="startCommentEdit(c)">수정</v-btn>
              <v-btn x-small text color="red" @click="deleteComment(c.commentId)">삭제</v-btn>
            </div>
          </template>
        </div>

        <v-row class="mt-3" align="center">
          <v-col>
            <div class="text-caption mb-1" style="color:#546E7A;">
              <v-icon x-small class="mr-1">mdi-account</v-icon>{{ currentUserId }}
            </div>
            <v-textarea v-model="newComment" rows="2" dense outlined hide-details placeholder="댓글을 입력하세요" color="ndColor"></v-textarea>
          </v-col>
          <v-col cols="auto">
            <v-btn class="gradient" @click="submitComment" :disabled="!newComment" style="height:52px;">등록</v-btn>
          </v-col>
        </v-row>
      </v-sheet>
    </template>

    <!-- 글쓰기/수정 모드 -->
    <template v-if="viewMode === 'write'">
      <v-sheet class="mx-4 mt-4 pa-4" outlined style="border-radius:8px;">
        <div class="d-flex align-center mb-3">
          <div class="text-h6 font-weight-bold">{{ isEdit ? '글 수정' : '새 글 작성' }}</div>
          <v-spacer></v-spacer>
          <div class="text-caption grey--text">
            <v-icon x-small class="mr-1">mdi-account</v-icon>{{ currentUserId }}
          </div>
        </div>
        <v-text-field v-model="writeForm.title" label="제목" outlined dense color="ndColor" class="mb-3"></v-text-field>
        <v-textarea v-model="writeForm.content" label="내용" outlined rows="10" color="ndColor"></v-textarea>

        <!-- 우선노출 (관리자만) -->
        <v-checkbox v-if="isAdmin" v-model="writeForm.pinYn" label="우선노출 (상단 고정)" color="ndColor"
          true-value="Y" false-value="N" hide-details class="mt-0 mb-2"></v-checkbox>

        <!-- 파일 첨부 -->
        <div class="mb-3">
          <v-file-input v-model="writeForm.files" label="파일 첨부" outlined dense multiple chips small-chips
            truncate-length="30" prepend-icon="mdi-paperclip" color="ndColor" hide-details></v-file-input>
        </div>

        <!-- 기존 첨부파일 (수정 시) -->
        <div v-if="isEdit && existingFiles.length > 0" class="mb-3">
          <div class="text-caption font-weight-bold mb-1">기존 첨부파일</div>
          <div v-for="f in existingFiles" :key="f.fileId" class="d-flex align-center mb-1">
            <span class="text-caption">{{ f.fileNm }}</span>
            <v-btn x-small icon color="red" @click="removeExistingFile(f.fileId)">
              <v-icon x-small>mdi-close</v-icon>
            </v-btn>
          </div>
        </div>

        <v-row justify="end" class="mt-2">
          <v-col cols="auto">
            <v-btn outlined @click="cancelWrite">취소</v-btn>
          </v-col>
          <v-col cols="auto">
            <v-btn class="gradient" @click="submitPost" :disabled="!writeForm.title" :loading="submitting">
              {{ isEdit ? '수정' : '등록' }}
            </v-btn>
          </v-col>
        </v-row>
      </v-sheet>
    </template>
  </v-main>
</template>

<script>
import axios from "axios";

export default {
  name: 'DSBoard',
  props: {
    isMobile: Boolean,
    boardType: { type: String, default: 'NOTICE' }
  },
  data: () => ({
    viewMode: 'list',
    loading: false,
    submitting: false,
    boardList: [],
    page: 1,
    pageCount: 0,
    itemsPerPage: 20,
    totalCount: 0,
    keyword: '',
    searchField: 'all',
    searchFieldOptions: [
      { label: '전체', value: 'all' },
      { label: '제목', value: 'title' },
      { label: '내용', value: 'content' },
      { label: '작성자', value: 'user' },
    ],
    isAdmin: false,
    currentUserId: '',
    currentPost: {},
    comments: [],
    newComment: '',
    attachFiles: [],
    editingCommentId: null,
    editCommentContent: '',
    writeForm: { title: '', content: '', pinYn: 'N', files: [] },
    existingFiles: [],
    isEdit: false,
    editBoardId: null,
    listHeaders: [
      { text: 'No', value: 'rowNum', align: 'center', sortable: false, width: '60px' },
      { text: '제목', value: 'title', align: 'left', sortable: false },
      { text: '작성자', value: 'cretUserId', align: 'center', sortable: false, width: '120px' },
      { text: '작성일', value: 'cretDt', align: 'center', sortable: false, width: '150px' },
      { text: '조회수', value: 'viewCnt', align: 'center', sortable: false, width: '80px' },
    ],
  }),
  watch: {
    page() { this.loadList(); }
  },
  created() {
    this.currentUserId = this.$loginStatusData.id;
    this.adminCheck();
    this.loadList();
  },
  methods: {
    loadList() {
      this.loading = true;
      var params = {
        boardType: this.boardType,
        keyword: this.keyword || '',
        searchField: this.searchField,
        limit: this.itemsPerPage,
        offset: (this.page - 1) * this.itemsPerPage
      };
      axios.post(this.$APIURL.base + "api/board/list", params)
        .then(res => {
          var list = res.data.list || [];
          this.totalCount = res.data.totalCount || 0;
          this.pageCount = Math.ceil(this.totalCount / this.itemsPerPage);
          // 순번 부여 (우선노출은 핀 아이콘, 나머지는 역순 번호)
          var offset = (this.page - 1) * this.itemsPerPage;
          for (var i = 0; i < list.length; i++) {
            list[i].rowNum = list[i].pinYn === 'Y' ? '' : (this.totalCount - offset - i);
          }
          this.boardList = list;
        })
        .catch(() => { this.$swal.fire({ title: '목록 조회 실패', icon: 'error', confirmButtonText: '확인' }); })
        .finally(() => { this.loading = false; });
    },
    viewPost(item) {
      axios.get(this.$APIURL.base + "api/board/" + item.boardId).then(res => {
        this.currentPost = res.data;
        this.viewMode = 'detail';
        this.editingCommentId = null;
        this.loadComments(item.boardId);
        this.loadFiles(item.boardId);
      });
    },
    loadComments(boardId) {
      axios.get(this.$APIURL.base + "api/board/" + boardId + "/comments")
        .then(res => { this.comments = res.data || []; });
    },
    loadFiles(boardId) {
      axios.get(this.$APIURL.base + "api/board/" + boardId + "/files")
        .then(res => { this.attachFiles = res.data || []; })
        .catch(() => { this.attachFiles = []; });
    },
    submitComment() {
      if (!this.newComment) return;
      axios.post(this.$APIURL.base + "api/board/" + this.currentPost.boardId + "/comment", { content: this.newComment })
        .then(res => { if (res.data.resultCode === 200) { this.newComment = ''; this.loadComments(this.currentPost.boardId); } });
    },
    startCommentEdit(c) { this.editingCommentId = c.commentId; this.editCommentContent = c.content; },
    cancelCommentEdit() { this.editingCommentId = null; this.editCommentContent = ''; },
    saveCommentEdit(commentId) {
      if (!this.editCommentContent.trim()) return;
      axios.post(this.$APIURL.base + "api/board/comment/update", { commentId: commentId, content: this.editCommentContent })
        .then(res => { if (res.data.resultCode === 200) { this.cancelCommentEdit(); this.loadComments(this.currentPost.boardId); } })
        .catch(() => { this.$swal.fire({ title: '수정 실패', icon: 'error', confirmButtonText: '확인' }); });
    },
    deleteComment(commentId) {
      this.$swal.fire({ title: '댓글을 삭제하시겠습니까?', icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' })
        .then(r => { if (r.isConfirmed) { axios.post(this.$APIURL.base + "api/board/comment/delete", { commentId }).then(() => this.loadComments(this.currentPost.boardId)); } });
    },
    showWriteForm() {
      this.isEdit = false;
      this.editBoardId = null;
      this.writeForm = { title: '', content: '', pinYn: 'N', files: [] };
      this.existingFiles = [];
      this.viewMode = 'write';
    },
    editPost() {
      this.isEdit = true;
      this.editBoardId = this.currentPost.boardId;
      this.writeForm = { title: this.currentPost.title, content: this.currentPost.content, pinYn: this.currentPost.pinYn || 'N', files: [] };
      this.existingFiles = this.attachFiles.slice();
      this.viewMode = 'write';
    },
    cancelWrite() {
      this.$swal.fire({ title: '작성을 취소하시겠습니까?', text: '입력한 내용은 저장되지 않습니다.', icon: 'warning', showCancelButton: true, confirmButtonText: '취소하기', cancelButtonText: '계속 작성' })
        .then(r => { if (r.isConfirmed) { this.viewMode = this.isEdit ? 'detail' : 'list'; } });
    },
    submitPost() {
      if (!this.writeForm.title) { this.$swal.fire({ title: '제목을 입력하세요', icon: 'warning', confirmButtonText: '확인' }); return; }
      this.submitting = true;
      var formData = new FormData();
      formData.append('boardType', this.boardType);
      formData.append('title', this.writeForm.title);
      formData.append('content', this.writeForm.content || '');
      formData.append('pinYn', this.writeForm.pinYn || 'N');
      if (this.isEdit) formData.append('boardId', this.editBoardId);
      // 새 파일
      if (this.writeForm.files && this.writeForm.files.length > 0) {
        for (var i = 0; i < this.writeForm.files.length; i++) {
          formData.append('files', this.writeForm.files[i]);
        }
      }
      var url = this.isEdit ? "api/board/updateWithFile" : "api/board/createWithFile";
      axios.post(this.$APIURL.base + url, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(res => {
          if (res.data.resultCode === 200) {
            this.$swal.fire({ title: this.isEdit ? '수정 완료' : '등록 완료', icon: 'success', timer: 1500, showConfirmButton: false });
            this.viewMode = 'list';
            this.loadList();
          } else {
            this.$swal.fire({ title: '실패', text: res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        })
        .catch(() => { this.$swal.fire({ title: '서버 오류', icon: 'error', confirmButtonText: '확인' }); })
        .finally(() => { this.submitting = false; });
    },
    removeExistingFile(fileId) {
      axios.post(this.$APIURL.base + "api/board/file/delete", { fileId: fileId })
        .then(() => { this.existingFiles = this.existingFiles.filter(function(f) { return f.fileId !== fileId; }); });
    },
    deletePost() {
      this.$swal.fire({ title: "'" + this.currentPost.title + "' 게시글을 삭제하시겠습니까?", icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소' })
        .then(r => { if (r.isConfirmed) { axios.post(this.$APIURL.base + "api/board/delete", { boardId: this.currentPost.boardId }).then(res => { if (res.data.resultCode === 200) { this.viewMode = 'list'; this.loadList(); } }); } });
    },
    goBack() { this.viewMode = 'list'; },
    formatFileSize(bytes) {
      if (!bytes) return '0B';
      if (bytes < 1024) return bytes + 'B';
      if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB';
      return (bytes / 1048576).toFixed(1) + 'MB';
    },
    adminCheck() {
      axios.get(this.$APIURL.base + "api/login/isAdmin", { params: { user: this.currentUserId } })
        .then(result => { this.isAdmin = result.data; });
    }
  },
  mounted() { this.$resizableGrid(); }
}
</script>

<style scoped>
.post-content { font-size: 0.9rem; line-height: 1.6; }
</style>

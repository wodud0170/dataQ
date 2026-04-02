<template>
  <v-main>
    <!-- 유형 탭 -->
    <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
      <v-tabs v-model="boardTab" color="ndColor" background-color="transparent">
        <v-tab @click="switchType('NOTICE')">공지사항</v-tab>
        <v-tab @click="switchType('QNA')">QNA</v-tab>
      </v-tabs>
    </v-sheet>

    <!-- 목록 모드 -->
    <template v-if="viewMode === 'list'">
      <v-sheet class="pa-4" style="background: #ffffff; border-bottom: 1px solid #E8EAF6;">
        <v-row align="center" no-gutters>
          <v-col cols="4">
            <v-text-field dense hide-details v-model="keyword" placeholder="검색어" color="ndColor"
              @keyup.enter="loadList" clearable></v-text-field>
          </v-col>
          <v-col cols="1" class="pl-2">
            <v-btn class="gradient" @click="loadList" style="min-width: 45px;">
              <v-icon>search</v-icon>
            </v-btn>
          </v-col>
          <v-spacer></v-spacer>
          <v-col cols="auto">
            <v-btn class="gradient" @click="showWriteForm"
              v-if="boardType === 'QNA' || isAdmin">
              <v-icon small class="mr-1">mdi-pencil</v-icon> 글쓰기
            </v-btn>
          </v-col>
        </v-row>
      </v-sheet>

      <v-data-table :headers="listHeaders" :items="boardList" :page.sync="page"
        :items-per-page="itemsPerPage" hide-default-footer item-key="boardId"
        class="px-4 pb-3" :loading="loading" @click:row="viewPost">
        <template v-slot:[`item.title`]="{ item }">
          <span class="ndColor--text" style="cursor: pointer;">{{ item.title }}</span>
        </template>
        <template v-slot:no-data>
          <v-alert>게시글이 없습니다.</v-alert>
        </template>
      </v-data-table>

      <div class="text-center px-4 pt-4 pb-4" v-show="pageCount > 1">
        <v-pagination v-model="page" :length="pageCount" prev-icon="mdi-menu-left" next-icon="mdi-menu-right"
          color="ndColor" :total-visible="10"></v-pagination>
      </div>
    </template>

    <!-- 상세 모드 -->
    <template v-if="viewMode === 'detail'">
      <v-sheet class="mx-4 mt-4 pa-4" outlined style="border-radius: 8px;">
        <v-row>
          <v-col cols="12">
            <v-btn text small @click="goBack" class="mb-2">
              <v-icon small class="mr-1">mdi-arrow-left</v-icon> 목록으로
            </v-btn>
            <div class="text-h6 font-weight-bold">{{ currentPost.title }}</div>
            <div class="grey--text text-caption mt-1">
              {{ currentPost.cretUserId }} | {{ currentPost.cretDt }}
              <span v-if="currentPost.updtDt"> | 수정: {{ currentPost.updtDt }}</span>
              | 조회 {{ currentPost.viewCnt }}
            </div>
          </v-col>
        </v-row>
        <v-divider class="my-3"></v-divider>
        <v-row>
          <v-col cols="12">
            <div class="post-content" style="min-height: 200px; white-space: pre-wrap;">{{ currentPost.content }}</div>
          </v-col>
        </v-row>

        <!-- 수정/삭제 버튼 -->
        <v-row v-if="currentPost.cretUserId === currentUserId || isAdmin" class="mt-2">
          <v-col cols="auto">
            <v-btn small outlined color="ndColor" @click="editPost">수정</v-btn>
          </v-col>
          <v-col cols="auto">
            <v-btn small outlined color="red" @click="deletePost">삭제</v-btn>
          </v-col>
        </v-row>

        <v-divider class="my-4"></v-divider>

        <!-- 댓글 목록 -->
        <div class="text-subtitle-2 font-weight-bold mb-2">댓글 ({{ comments.length }})</div>
        <div v-for="c in comments" :key="c.commentId" class="comment-item pa-2 mb-2" style="background: #F5F7FA; border-radius: 4px;">
          <div class="d-flex justify-space-between">
            <span class="text-caption font-weight-bold">{{ c.cretUserId }}</span>
            <span class="text-caption grey--text">{{ c.cretDt }}</span>
          </div>
          <div class="text-body-2 mt-1" style="white-space: pre-wrap;">{{ c.content }}</div>
          <v-btn v-if="c.cretUserId === currentUserId || isAdmin" x-small text color="red"
            @click="deleteComment(c.commentId)">삭제</v-btn>
        </div>

        <!-- 댓글 작성 -->
        <v-row class="mt-3" align="center">
          <v-col cols="10">
            <v-textarea v-model="newComment" rows="2" dense outlined hide-details placeholder="댓글을 입력하세요"
              color="ndColor"></v-textarea>
          </v-col>
          <v-col cols="2">
            <v-btn class="gradient" block @click="submitComment" :disabled="!newComment">등록</v-btn>
          </v-col>
        </v-row>
      </v-sheet>
    </template>

    <!-- 글쓰기/수정 모드 -->
    <template v-if="viewMode === 'write'">
      <v-sheet class="mx-4 mt-4 pa-4" outlined style="border-radius: 8px;">
        <v-btn text small @click="goBack" class="mb-2">
          <v-icon small class="mr-1">mdi-arrow-left</v-icon> 취소
        </v-btn>
        <div class="text-h6 font-weight-bold mb-3">{{ isEdit ? '글 수정' : '새 글 작성' }}</div>
        <v-text-field v-model="writeForm.title" label="제목" outlined dense color="ndColor" class="mb-3"></v-text-field>
        <v-textarea v-model="writeForm.content" label="내용" outlined rows="10" color="ndColor"></v-textarea>
        <v-row justify="end" class="mt-2">
          <v-col cols="auto">
            <v-btn class="gradient" @click="submitPost" :disabled="!writeForm.title">
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
  props: ['isMobile'],
  data: () => ({
    boardTab: 0,
    boardType: 'NOTICE',
    viewMode: 'list', // list, detail, write
    loading: false,
    boardList: [],
    page: 1,
    pageCount: 0,
    itemsPerPage: 20,
    totalCount: 0,
    keyword: '',
    isAdmin: false,
    currentUserId: '',
    // 상세
    currentPost: {},
    comments: [],
    newComment: '',
    // 글쓰기
    writeForm: { title: '', content: '' },
    isEdit: false,
    editBoardId: null,
    listHeaders: [
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
    switchType(type) {
      this.boardType = type;
      this.page = 1;
      this.viewMode = 'list';
      this.loadList();
    },
    loadList() {
      this.loading = true;
      let params = {
        boardType: this.boardType,
        keyword: this.keyword || '',
        limit: this.itemsPerPage,
        offset: (this.page - 1) * this.itemsPerPage
      };
      axios.post(this.$APIURL.base + "api/board/list", params)
        .then(res => {
          this.boardList = res.data.list || [];
          this.totalCount = res.data.totalCount || 0;
          this.pageCount = Math.ceil(this.totalCount / this.itemsPerPage);
        })
        .catch(err => console.error(err))
        .finally(() => { this.loading = false; });
    },
    viewPost(item) {
      axios.get(this.$APIURL.base + "api/board/" + item.boardId)
        .then(res => {
          this.currentPost = res.data;
          this.viewMode = 'detail';
          this.loadComments(item.boardId);
        });
    },
    loadComments(boardId) {
      axios.get(this.$APIURL.base + "api/board/" + boardId + "/comments")
        .then(res => { this.comments = res.data || []; });
    },
    submitComment() {
      if (!this.newComment) return;
      axios.post(this.$APIURL.base + "api/board/" + this.currentPost.boardId + "/comment", {
        content: this.newComment
      }).then(res => {
        if (res.data.resultCode === 200) {
          this.newComment = '';
          this.loadComments(this.currentPost.boardId);
        }
      });
    },
    deleteComment(commentId) {
      this.$swal.fire({
        title: '댓글을 삭제하시겠습니까?',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: '삭제',
        cancelButtonText: '취소'
      }).then(r => {
        if (r.isConfirmed) {
          axios.post(this.$APIURL.base + "api/board/comment/delete", { commentId })
            .then(() => this.loadComments(this.currentPost.boardId));
        }
      });
    },
    showWriteForm() {
      this.isEdit = false;
      this.editBoardId = null;
      this.writeForm = { title: '', content: '' };
      this.viewMode = 'write';
    },
    editPost() {
      this.isEdit = true;
      this.editBoardId = this.currentPost.boardId;
      this.writeForm = { title: this.currentPost.title, content: this.currentPost.content };
      this.viewMode = 'write';
    },
    submitPost() {
      if (!this.writeForm.title) return;
      let url = this.isEdit ? "api/board/update" : "api/board/create";
      let params = {
        boardType: this.boardType,
        title: this.writeForm.title,
        content: this.writeForm.content
      };
      if (this.isEdit) params.boardId = this.editBoardId;

      axios.post(this.$APIURL.base + url, params)
        .then(res => {
          if (res.data.resultCode === 200) {
            this.$swal.fire({ title: this.isEdit ? '수정 완료' : '등록 완료', icon: 'success', timer: 1500, showConfirmButton: false });
            this.viewMode = 'list';
            this.loadList();
          } else {
            this.$swal.fire({ title: '실패', text: res.data.resultMessage, icon: 'error', confirmButtonText: '확인' });
          }
        });
    },
    deletePost() {
      this.$swal.fire({
        title: '게시글을 삭제하시겠습니까?',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: '삭제',
        cancelButtonText: '취소'
      }).then(r => {
        if (r.isConfirmed) {
          axios.post(this.$APIURL.base + "api/board/delete", { boardId: this.currentPost.boardId })
            .then(res => {
              if (res.data.resultCode === 200) {
                this.viewMode = 'list';
                this.loadList();
              }
            });
        }
      });
    },
    goBack() {
      if (this.viewMode === 'write' && this.isEdit) {
        this.viewMode = 'detail';
      } else {
        this.viewMode = 'list';
      }
    },
    adminCheck() {
      axios.get(this.$APIURL.base + "api/login/isAdmin", { params: { user: this.currentUserId } })
        .then(result => { this.isAdmin = result.data; })
        .catch(err => { console.log(err); });
    }
  },
  mounted() {
    this.$resizableGrid();
  }
}
</script>

<style scoped>
.post-content {
  font-size: 0.9rem;
  line-height: 1.6;
}
</style>

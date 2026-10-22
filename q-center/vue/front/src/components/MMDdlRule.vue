<!--
  104번 WP3 — DDL 규칙 (데이터 소스 관리 권한)

  PK 이름 규칙과 데이터 타입 매핑. 데이터 모델 DDL 내려받기, 변경 이력의 DDL 조각, [DB 반영] 이 이 규칙으로 DDL 을 만든다.
-->
<template>
  <v-container fluid class="pa-2" style="height:100%; display:flex; flex-direction:column;">
    <v-sheet class="pa-2 mb-2 d-flex align-center" style="background:#F5F7FA; border-radius:4px; border:1px solid #E8EAF6;">
      <v-icon small color="#3F51B5" class="mr-2">mdi-information-outline</v-icon>
      <span style="font-size:.8rem; color:#546E7A;">
        데이터 모델의 DDL 내려받기, 변경 이력의 DDL, DB 반영이 이 규칙을 씁니다.
        타입은 모델 데이터소스의 DBMS 행을 찾고, 행이 없는 타입은 DDL 을 만들지 않습니다(DB 반영 거부, 내려받기는 그 테이블을 주석으로).
      </span>
    </v-sheet>

    <!-- PK 이름 규칙 -->
    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span style="font-size:.85rem; font-weight:600; min-width:110px;">PK 이름 규칙</span>
      <v-text-field v-model="pkTemplate" dense hide-details outlined style="max-width:260px;"
        placeholder="PK_{TABLE}" />
      <span style="font-size:.78rem; color:#78909C;">
        {TABLE} = 테이블 이름. 예) {{ pkExample }}
      </span>
      <v-spacer />
      <v-btn small color="primary" :disabled="!pkTemplate" @click="savePk">저장</v-btn>
    </v-sheet>

    <!-- 데이터 타입 매핑 -->
    <v-sheet class="d-flex align-center pa-2 mb-2" style="gap:8px; border:1px solid #e0e0e0; border-radius:4px;">
      <span style="font-size:.85rem; font-weight:600; min-width:110px;">데이터 타입 매핑</span>
      <v-btn-toggle v-model="dbmsTp" mandatory dense color="primary">
        <v-btn v-for="d in dbmsList" :key="d" :value="d" small>{{ d }}</v-btn>
      </v-btn-toggle>
      <v-spacer />
      <v-btn small color="primary" @click="openAdd">매핑 추가</v-btn>
      <v-btn small :disabled="!selected" @click="openEdit">수정</v-btn>
      <v-btn small color="error" :disabled="!selected" @click="confirmDelete">삭제</v-btn>
    </v-sheet>

    <v-data-table :headers="headers" :items="items" dense hide-default-footer
      :items-per-page="-1" item-key="dataType" single-select show-select
      v-model="selectedRows" class="elevation-0" style="flex:1; overflow:auto;">
      <template v-slot:item.physDataType="{ item }">
        <code>{{ item.physDataType }}</code>
      </template>
      <template v-slot:item.example="{ item }">
        <span style="font-family:monospace; font-size:.8rem;">{{ exampleOf(item) }}</span>
      </template>
    </v-data-table>

    <!-- 추가 / 수정 -->
    <v-dialog max-width="620" v-model="modal">
      <v-card>
        <v-card-title>{{ form.isNew ? '데이터 타입 매핑 추가' : '데이터 타입 매핑 수정' }} — {{ form.dbmsTp }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.dataType" label="데이터 타입" dense :disabled="!form.isNew"
            hint="모델·도메인의 데이터 타입 (예: VARCHAR, NUMBER, DATETIME)" persistent-hint />
          <v-text-field v-model="form.physDataType" label="물리 타입" dense class="mt-3"
            hint="{len} = 길이, {dec} = 소수점, [ ] = 안의 값이 모두 있을 때만 붙는 부분. 예) NUMBER[({len}[,{dec}])]" persistent-hint />
          <v-text-field v-model="form.dfltDataLen" label="기본 길이" dense class="mt-3" type="number"
            hint="길이가 없는 컬럼에 쓸 길이. [ ] 밖에 {len} 이 있으면 필수" persistent-hint />
          <div class="mt-4" style="font-size:.8rem; color:#546E7A;">
            미리보기 —
            길이 없음: <code>{{ preview.none }}</code>,
            길이 10: <code>{{ preview.len }}</code>,
            길이 10·소수점 2: <code>{{ preview.dec }}</code>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="modal = false">취소</v-btn>
          <v-btn color="primary" :disabled="!form.dataType || !form.physDataType" @click="save">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000">{{ snackbarMsg }}</v-snackbar>
  </v-container>
</template>

<script>
import axios from 'axios';

/** 서버 DdlRuleService.render 와 같은 규칙 — [ ] 는 안쪽부터, 안의 {len}·{dec} 가 0 보다 클 때만 남긴다 */
function render(tmpl, len, dec) {
  let s = tmpl || '';
  const re = /\[([^[\]]*)\]/;
  let m;
  while ((m = re.exec(s)) !== null) {
    const body = m[1];
    const keep = (body.indexOf('{len}') < 0 || len > 0) && (body.indexOf('{dec}') < 0 || dec > 0);
    s = s.substring(0, m.index) + (keep ? body : '') + s.substring(m.index + m[0].length);
  }
  return s.split('{len}').join(String(len)).split('{dec}').join(String(dec));
}

export default {
  name: 'MMDdlRule',
  props: ['isMobile'],
  data() {
    return {
      pkTemplate: '',
      dbmsList: [],
      dbmsTp: 'Oracle',
      items: [],
      selectedRows: [],
      modal: false,
      form: { isNew: true, dataType: '', dbmsTp: '', physDataType: '', dfltDataLen: '' },
      snackbar: false, snackbarMsg: '', snackbarColor: 'info',
      headers: [
        { text: '데이터 타입', value: 'dataType', width: '160px' },
        { text: '물리 타입', value: 'physDataType' },
        { text: '기본 길이', value: 'dfltDataLen', width: '100px', align: 'end' },
        { text: '예 (길이 10·소수점 2 / 길이 없음)', value: 'example', sortable: false },
        { text: '수정자', value: 'updtUserId', width: '110px' },
      ],
    };
  },
  computed: {
    selected() { return this.selectedRows.length ? this.selectedRows[0] : null; },
    pkExample() { return (this.pkTemplate || '').split('{TABLE}').join('TB_CUST'); },
    preview() {
      const t = this.form.physDataType;
      const d = Number(this.form.dfltDataLen) || 0;
      return { none: render(t, d, 0), len: render(t, 10, 0), dec: render(t, 10, 2) };
    },
  },
  watch: {
    dbmsTp() { this.selectedRows = []; this.load(); },
  },
  mounted() {
    this.loadPk();
    this.load();
  },
  methods: {
    exampleOf(item) {
      const d = Number(item.dfltDataLen) || 0;
      return render(item.physDataType, 10, 2) + '  /  ' + render(item.physDataType, d, 0);
    },
    loadPk() {
      axios.get(this.$APIURL.base + 'api/ddlRule/pkTemplate').then(res => {
        this.pkTemplate = (res.data && res.data.contents) || '';
      }).catch(() => this.notify('PK 이름 규칙 조회 실패', 'error'));
    },
    load() {
      axios.get(this.$APIURL.base + 'api/ddlRule/typeMap/list', { params: { dbmsTp: this.dbmsTp } }).then(res => {
        if (!res.data || res.data.resultCode !== 200) {
          this.notify('조회 실패: ' + (res.data && res.data.resultMessage), 'error');
          return;
        }
        const c = JSON.parse(res.data.contents);
        this.dbmsList = c.dbmsList || [];
        this.items = c.items || [];
      }).catch(() => this.notify('매핑 목록 조회 실패', 'error'));
    },
    savePk() {
      axios.post(this.$APIURL.base + 'api/ddlRule/pkTemplate/save', { pkTemplate: this.pkTemplate }).then(res => {
        if (res.data && res.data.resultCode === 200) this.notify('저장했습니다.', 'success');
        else this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    openAdd() {
      this.form = { isNew: true, dataType: '', dbmsTp: this.dbmsTp, physDataType: '', dfltDataLen: '' };
      this.modal = true;
    },
    openEdit() {
      if (!this.selected) return;
      const s = this.selected;
      this.form = { isNew: false, dataType: s.dataType, dbmsTp: s.dbmsTp, physDataType: s.physDataType,
        dfltDataLen: s.dfltDataLen == null ? '' : String(s.dfltDataLen) };
      this.modal = true;
    },
    save() {
      axios.post(this.$APIURL.base + 'api/ddlRule/typeMap/save', this.form).then(res => {
        if (res.data && res.data.resultCode === 200) {
          this.notify('저장했습니다.', 'success');
          this.modal = false;
          this.selectedRows = [];
          this.load();
        } else {
          this.notify('저장 실패: ' + (res.data && res.data.resultMessage), 'error');
        }
      }).catch(() => this.notify('서버 오류', 'error'));
    },
    confirmDelete() {
      if (!this.selected) return;
      const s = this.selected;
      this.$swal.fire({
        title: s.dbmsTp + ' 의 ' + s.dataType + ' 매핑을 삭제할까요?',
        text: '삭제하면 이 DBMS 에서 이 타입을 쓰는 컬럼은 DDL 을 만들 수 없습니다.',
        icon: 'warning', showCancelButton: true, confirmButtonText: '삭제', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/ddlRule/typeMap/delete', { dataType: s.dataType, dbmsTp: s.dbmsTp }).then(res => {
          if (res.data && res.data.resultCode === 200) {
            this.notify('삭제했습니다.', 'success');
            this.selectedRows = [];
            this.load();
          } else {
            this.notify('삭제 실패: ' + (res.data && res.data.resultMessage), 'error');
          }
        }).catch(() => this.notify('서버 오류', 'error'));
      });
    },
    notify(msg, color) {
      this.snackbarMsg = msg; this.snackbarColor = color || 'info'; this.snackbar = true;
    },
  },
};
</script>

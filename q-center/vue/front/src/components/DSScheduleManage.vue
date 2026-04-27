<template>
  <v-main>
    <v-sheet class="pa-3" style="display:flex; flex-direction:column; height:100%;">
      <!-- 상단: 필터 + 등록 버튼 -->
      <v-sheet class="d-flex align-center pa-2" style="border-bottom:1px solid #E8EAF6; gap:6px;">
        <v-chip small class="mr-1" :color="filterUseYn === '' ? 'primary' : ''"
          :outlined="filterUseYn !== ''" @click="filterUseYn = ''">전체</v-chip>
        <v-chip small class="mr-1" :color="filterUseYn === 'Y' ? 'green' : ''"
          :text-color="filterUseYn === 'Y' ? 'white' : ''"
          :outlined="filterUseYn !== 'Y'" @click="filterUseYn = 'Y'">활성만</v-chip>
        <v-chip small class="mr-1" :color="filterUseYn === 'N' ? 'grey' : ''"
          :text-color="filterUseYn === 'N' ? 'white' : ''"
          :outlined="filterUseYn !== 'N'" @click="filterUseYn = 'N'">비활성만</v-chip>
        <v-spacer></v-spacer>
        <v-btn v-if="isAdmin" small class="gradient" @click="openAddDialog">
          <v-icon small left>mdi-plus</v-icon>스케줄 추가
        </v-btn>
        <span v-else style="font-size:.75rem; color:#9E9E9E;">조회 전용 (관리자만 등록/수정 가능)</span>
      </v-sheet>

      <!-- 목록 -->
      <v-data-table :headers="headers" :items="filteredItems" dense hide-default-footer
        :items-per-page="30" class="elevation-0" :loading="loading">
        <template v-slot:item.useYn="{ item }">
          <v-switch v-model="item._useYnBool" inset hide-details dense
            color="green" class="mt-0 ml-2" :ripple="false" :disabled="!isAdmin"
            @change="toggleUseYn(item)"></v-switch>
        </template>
        <template v-slot:item.diagType="{ item }">
          <v-chip x-small :color="diagTypeColor(item.diagType)" text-color="white">
            {{ diagTypeLabel(item.diagType) }}
          </v-chip>
        </template>
        <template v-slot:item.schedule="{ item }">
          <span style="font-size:.8rem;">{{ formatSchedule(item) }}</span>
        </template>
        <template v-slot:item.nextRun="{ item }">
          <span style="font-size:.8rem; color:#546E7A;">{{ nextRunText(item) }}</span>
        </template>
        <template v-slot:item.lastExec="{ item }">
          <div v-if="item.lastExecDt">
            <v-chip x-small :color="lastStatusColor(item.lastExecStatus)" text-color="white">
              {{ item.lastExecStatus || '-' }}
            </v-chip>
            <span style="font-size:.75rem; color:#546E7A; margin-left:4px;">
              {{ shortDt(item.lastExecDt) }}
            </span>
          </div>
          <span v-else style="font-size:.75rem; color:#9E9E9E;">-</span>
        </template>
        <template v-slot:item.actions="{ item }">
          <template v-if="isAdmin">
            <v-btn icon x-small color="primary" @click="runNow(item)" :loading="item._running"
              title="즉시 실행">
              <v-icon small>mdi-play</v-icon>
            </v-btn>
            <v-btn icon x-small color="grey darken-2" @click="openEditDialog(item)" title="편집">
              <v-icon small>mdi-pencil</v-icon>
            </v-btn>
            <v-menu offset-y>
              <template v-slot:activator="{ on, attrs }">
                <v-btn icon x-small v-on="on" v-bind="attrs"><v-icon small>mdi-dots-vertical</v-icon></v-btn>
              </template>
              <v-list dense>
                <v-list-item @click="confirmDelete(item)">
                  <v-list-item-icon><v-icon small color="red">mdi-delete</v-icon></v-list-item-icon>
                  <v-list-item-title class="red--text">삭제</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </template>
          <span v-else style="font-size:.75rem; color:#9E9E9E;">-</span>
        </template>
        <template #no-data>
          <span class="grey--text">스케줄이 없습니다.</span>
        </template>
      </v-data-table>
    </v-sheet>

    <!-- 등록/수정 다이얼로그 -->
    <v-dialog v-model="editDialog" max-width="760" persistent>
      <v-card>
        <v-card-title>{{ editMode === 'add' ? '스케줄 등록' : '스케줄 수정' }}</v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12">
              <v-text-field v-model="form.scheduleNm" label="스케줄명 *" dense outlined hide-details></v-text-field>
            </v-col>
            <v-col cols="6">
              <v-select v-model="form.diagType" :items="diagTypeOptions" label="진단 유형 *"
                item-text="label" item-value="value" dense outlined hide-details></v-select>
            </v-col>
            <v-col cols="6">
              <v-autocomplete v-model="form.dataModelId" :items="dataModels" label="데이터모델 *"
                item-text="dataModelNm" item-value="dataModelId" dense outlined hide-details
                placeholder="모델 검색"></v-autocomplete>
            </v-col>
            <v-col cols="12">
              <v-radio-group v-model="form.scheduleType" row dense hide-details class="mt-1">
                <v-radio label="간편 설정" value="SIMPLE"></v-radio>
                <v-radio label="Cron 표현식" value="CRON"></v-radio>
              </v-radio-group>
            </v-col>
            <template v-if="form.scheduleType === 'SIMPLE'">
              <v-col cols="4">
                <v-select v-model="form.repeatCycle" :items="cycleOptions" label="반복 주기 *"
                  item-text="label" item-value="value" dense outlined hide-details></v-select>
              </v-col>
              <v-col cols="4">
                <v-text-field v-model="form.repeatTime" label="실행 시각 (HH:mm) *"
                  placeholder="02:30" dense outlined hide-details></v-text-field>
              </v-col>
              <v-col cols="4" v-if="form.repeatCycle === 'WEEKLY'">
                <v-select v-model="form.repeatDayOfWeek" :items="dowOptions" label="요일 *"
                  item-text="label" item-value="value" dense outlined hide-details></v-select>
              </v-col>
              <v-col cols="4" v-else-if="form.repeatCycle === 'MONTHLY'">
                <v-text-field v-model.number="form.repeatDayOfMonth" label="일자 (1~28) *"
                  type="number" min="1" max="28" dense outlined hide-details></v-text-field>
              </v-col>
            </template>
            <template v-else>
              <v-col cols="12">
                <v-text-field v-model="form.cronExpr" label="Cron 표현식 (초 분 시 일 월 요일)"
                  placeholder="0 0 2 * * MON-FRI" dense outlined hide-details
                  @input="previewCron" :hint="cronHint" persistent-hint></v-text-field>
              </v-col>
              <v-col cols="12" v-if="cronNextRuns.length">
                <div style="font-size:.8rem; color:#546E7A;">
                  <strong>다음 5회 실행 예정:</strong>
                  <ul class="mb-0" style="margin-top:4px; padding-left:16px;">
                    <li v-for="(t, i) in cronNextRuns" :key="i" style="font-size:.75rem;">{{ t }}</li>
                  </ul>
                </div>
              </v-col>
            </template>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn text @click="editDialog = false">취소</v-btn>
          <v-btn color="primary" @click="saveSchedule" :loading="saving">저장</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DSScheduleManage',
  data() {
    return {
      loading: false,
      saving: false,
      items: [],
      dataModels: [],
      isAdmin: false,
      filterUseYn: '',     // '', 'Y', 'N'
      headers: [
        { text: '스케줄명',    value: 'scheduleNm', sortable: false },
        { text: '진단 유형',   value: 'diagType',   sortable: false, align: 'center', width: 90 },
        { text: '데이터모델',  value: 'dataModelNm', sortable: false },
        { text: '주기',        value: 'schedule',   sortable: false },
        { text: '다음 실행',   value: 'nextRun',    sortable: false },
        { text: '활성',        value: 'useYn',      sortable: false, align: 'center', width: 80 },
        { text: '최종 실행',   value: 'lastExec',   sortable: false },
        { text: '작업',        value: 'actions',    sortable: false, align: 'center', width: 120 },
      ],
      diagTypeOptions: [
        { label: '표준화 진단',          value: 'STANDARD' },
        { label: '구조 변경 진단',        value: 'STRUCT' },
        { label: '전체 (표준화 + 구조)', value: 'BOTH' },
      ],
      cycleOptions: [
        { label: '매일',  value: 'DAILY' },
        { label: '매주',  value: 'WEEKLY' },
        { label: '매월',  value: 'MONTHLY' },
      ],
      dowOptions: [
        { label: '월', value: 1 }, { label: '화', value: 2 }, { label: '수', value: 3 },
        { label: '목', value: 4 }, { label: '금', value: 5 }, { label: '토', value: 6 },
        { label: '일', value: 7 },
      ],
      editDialog: false,
      editMode: 'add',
      form: this.emptyForm(),
      cronNextRuns: [],
      cronHint: '',
    };
  },
  computed: {
    filteredItems() {
      if (!this.filterUseYn) return this.items;
      return this.items.filter(s => s.useYn === this.filterUseYn);
    },
  },
  mounted() {
    this.checkAdmin();
    this.loadModels();
    this.loadList();
  },
  methods: {
    checkAdmin() {
      var self = this;
      axios.get(this.$APIURL.base + 'api/login/isAdmin', { params: { user: this.$loginStatusData && this.$loginStatusData.id } })
        .then(function(res) { self.isAdmin = res.data === true; })
        .catch(function() { self.isAdmin = false; });
    },
    emptyForm() {
      return {
        scheduleId: null,
        scheduleNm: '',
        diagType: 'STANDARD',
        dataModelId: null,
        scheduleType: 'SIMPLE',
        repeatCycle: 'DAILY',
        repeatTime: '02:00',
        repeatDayOfWeek: 1,
        repeatDayOfMonth: 1,
        cronExpr: '',
        useYn: 'Y',
      };
    },
    loadModels() {
      axios.post(this.$APIURL.base + 'api/dm/getDataModelStatsList', {})
        .then(res => { this.dataModels = res.data || []; });
    },
    loadList() {
      this.loading = true;
      axios.get(this.$APIURL.base + 'api/diag/schedule/list')
        .then(res => {
          this.items = (res.data || []).map(s => ({
            ...s,
            _useYnBool: s.useYn === 'Y',
            _running: false,
          }));
        })
        .finally(() => { this.loading = false; });
    },
    openAddDialog() {
      this.editMode = 'add';
      this.form = this.emptyForm();
      this.cronNextRuns = [];
      this.editDialog = true;
    },
    openEditDialog(item) {
      this.editMode = 'edit';
      this.form = {
        scheduleId: item.scheduleId,
        scheduleNm: item.scheduleNm,
        diagType: item.diagType,
        dataModelId: item.dataModelId,
        scheduleType: item.scheduleType || 'SIMPLE',
        repeatCycle: item.repeatCycle || 'DAILY',
        repeatTime: item.repeatTime || '02:00',
        repeatDayOfWeek: item.repeatDayOfWeek || 1,
        repeatDayOfMonth: item.repeatDayOfMonth || 1,
        cronExpr: item.cronExpr || '',
        useYn: item.useYn || 'Y',
      };
      this.cronNextRuns = [];
      this.editDialog = true;
      if (this.form.scheduleType === 'CRON' && this.form.cronExpr) this.previewCron();
    },
    saveSchedule() {
      const f = this.form;
      if (!f.scheduleNm) { this.$swal.fire({ title: '스케줄명 필수', icon: 'warning' }); return; }
      if (!f.dataModelId) { this.$swal.fire({ title: '데이터모델 선택 필요', icon: 'warning' }); return; }
      if (f.scheduleType === 'SIMPLE' && !/^[0-2]\d:[0-5]\d$/.test(f.repeatTime)) {
        this.$swal.fire({ title: '실행 시각 형식 오류 (HH:mm)', icon: 'warning' }); return;
      }
      if (f.scheduleType === 'CRON' && !f.cronExpr) {
        this.$swal.fire({ title: 'Cron 표현식 필수', icon: 'warning' }); return;
      }
      this.saving = true;
      const url = this.$APIURL.base + (this.editMode === 'add'
          ? 'api/diag/schedule/create' : 'api/diag/schedule/update');
      axios.post(url, f)
        .then(res => {
          const body = res.data || {};
          if (body.resultCode === 200) {
            this.editDialog = false;
            this.loadList();
            this.$swal.fire({ title: '저장됨', icon: 'success', timer: 1200, showConfirmButton: false });
          } else {
            this.$swal.fire({ title: '저장 실패', text: body.resultMessage, icon: 'error' });
          }
        })
        .finally(() => { this.saving = false; });
    },
    toggleUseYn(item) {
      const newYn = item._useYnBool ? 'Y' : 'N';
      axios.post(this.$APIURL.base + 'api/diag/schedule/toggle',
        { scheduleId: item.scheduleId, useYn: newYn })
        .then(res => {
          if ((res.data || {}).resultCode !== 200) {
            item._useYnBool = !item._useYnBool; // 롤백
          } else {
            item.useYn = newYn;
          }
        });
    },
    runNow(item) {
      this.$swal.fire({
        title: '즉시 실행',
        text: `"${item.scheduleNm}" 를 지금 실행합니다.`,
        icon: 'question', showCancelButton: true,
        confirmButtonText: '실행', cancelButtonText: '취소',
      }).then(r => {
        if (!r.isConfirmed) return;
        item._running = true;
        axios.post(this.$APIURL.base + 'api/diag/schedule/runNow', { scheduleId: item.scheduleId })
          .then(res => {
            const body = res.data || {};
            if (body.resultCode === 200) {
              this.$swal.fire({ title: '실행 요청 전송', text: '실행 이력에서 결과 확인', icon: 'success', timer: 1500 });
              setTimeout(() => this.loadList(), 2500);
            } else {
              this.$swal.fire({ title: '실행 실패', text: body.resultMessage, icon: 'error' });
            }
          })
          .finally(() => { item._running = false; });
      });
    },
    confirmDelete(item) {
      this.$swal.fire({
        title: '삭제 확인',
        html: `<strong>${item.scheduleNm}</strong> 을(를) 삭제합니다.<br>` +
              `평소에는 "비활성" 토글로 충분합니다. 정말 삭제하시겠습니까?`,
        icon: 'warning', showCancelButton: true,
        confirmButtonText: '삭제', cancelButtonText: '취소', confirmButtonColor: '#D32F2F',
      }).then(r => {
        if (!r.isConfirmed) return;
        axios.post(this.$APIURL.base + 'api/diag/schedule/delete', { scheduleId: item.scheduleId })
          .then(() => this.loadList());
      });
    },
    previewCron() {
      if (!this.form.cronExpr) { this.cronNextRuns = []; this.cronHint = ''; return; }
      axios.post(this.$APIURL.base + 'api/diag/schedule/cronPreview', { cronExpr: this.form.cronExpr })
        .then(res => {
          const body = res.data || {};
          if (body.resultCode === 200) {
            try {
              const p = JSON.parse(body.contents || '{}');
              this.cronNextRuns = p.next || [];
              this.cronHint = '';
            } catch (e) { this.cronHint = ''; }
          } else {
            this.cronNextRuns = [];
            this.cronHint = body.resultMessage || 'cron 표현식 오류';
          }
        });
    },
    diagTypeLabel(t) {
      return { STANDARD: '표준화', STRUCT: '구조변경', BOTH: '전체' }[t] || t;
    },
    diagTypeColor(t) {
      return { STANDARD: 'blue', STRUCT: 'deep-purple', BOTH: 'teal' }[t] || 'grey';
    },
    formatSchedule(s) {
      if (s.scheduleType === 'CRON') return '[CRON] ' + (s.cronExpr || '');
      if (s.repeatCycle === 'DAILY') return `매일 ${s.repeatTime || ''}`;
      if (s.repeatCycle === 'WEEKLY') {
        const dow = (this.dowOptions.find(o => o.value === s.repeatDayOfWeek) || {}).label || '';
        return `매주 ${dow} ${s.repeatTime || ''}`;
      }
      if (s.repeatCycle === 'MONTHLY') return `매월 ${s.repeatDayOfMonth || '-'}일 ${s.repeatTime || ''}`;
      return '-';
    },
    nextRunText(s) {
      if (s.useYn !== 'Y') return '(비활성)';
      // 간이 계산 (SIMPLE 만). CRON 은 서버 preview 호출 필요 — 목록에선 스킵.
      if (s.scheduleType !== 'SIMPLE' || !s.repeatTime) return '-';
      const [hh, mm] = s.repeatTime.split(':').map(Number);
      const now = new Date();
      const next = new Date(now);
      next.setHours(hh, mm, 0, 0);
      if (s.repeatCycle === 'DAILY') {
        if (next <= now) next.setDate(next.getDate() + 1);
      } else if (s.repeatCycle === 'WEEKLY') {
        const targetDow = s.repeatDayOfWeek; // 1~7
        let diff = targetDow - ((now.getDay() + 6) % 7 + 1); // now.getDay(): 일=0, 월=1...
        if (diff < 0 || (diff === 0 && next <= now)) diff += 7;
        next.setDate(next.getDate() + diff);
      } else if (s.repeatCycle === 'MONTHLY') {
        next.setDate(s.repeatDayOfMonth || 1);
        if (next <= now) next.setMonth(next.getMonth() + 1);
      } else return '-';
      return next.toLocaleString('sv-SE').substring(5, 16); // MM-DD HH:mm
    },
    lastStatusColor(s) {
      return { DONE: 'green', ERROR: 'red', SKIPPED: 'grey', RUNNING: 'blue' }[s] || 'grey';
    },
    shortDt(s) {
      if (!s) return '';
      return String(s).replace('T', ' ').substring(5, 16);
    },
  },
};
</script>

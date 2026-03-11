let navigationItem = [
    {
        icon:"dashboard", 
        title:"대시보드",
        index: "1",
        id:"nav_dashboard",
        href:"#tab_dashboard",
        children:[],
    },
    {
        icon:"task", 
        title:"데이터 표준",
        index: "2",
        id:"dsGroup",
        active:false,
        href:"",
        children: [
            { 
                icon: "", 
                title: "용어",
                index: "3",
                id:"nav_term",
                href:"#tab_term",
                children:[],
            },
            { 
                icon: "", 
                title: "단어",
                index: "4",
                id:"nav_word",
                href:"#tab_word",
                children:[],
            },
            { 
                icon: "", 
                title: "도메인",
                index: "5",
                id:"nav_domain",
                href:"#tab_domain",
                children:[],
            },
            { 
                icon: "", 
                title: "데이터 모델",
                index: "6",
                id:"nav_datamodel",
                href:"#tab_datamodel",
                children:[],
            },
        ]
    },
    {
        icon:"high_quality", 
        title:"데이터 품질",
        index: "7",
        id:"dqGroup",
        active:false,
        href:"",
        children: [
            { 
                icon: "", 
                title: "검증항목" ,
                index: "8",
                href:"",
                id:"navDqSub1",
                children: [
                    { 
                        icon: "", 
                        title: "품질지표(DQI)",
                        index: "9",
                        id:"nav_dqi",
                        href:"#tab_dqi",
                        children:[],
                    },
                    { 
                        icon: "", 
                        title: "핵심관리항목(CTQ)",
                        index: "10",
                        id:"nav_ctq",
                        href:"#tab_ctq",
                        children:[],
                    },
                    { 
                        icon: "", 
                        title: "업무규칙(BR)",
                        index: "11",
                        id:"nav_dqbr",
                        href:"#tab_dqbr",
                        children:[],
                    },
                ]
            },
            { 
                icon: "", 
                title: "품질검증",
                index: "12",
                href:"",
                id:"navDqSub2",
                children: [
                    { 
                        icon: "", 
                        title: "검증대상",
                        index: "13",
                        id:"nav_target",
                        href:"#tab_target",
                        children:[],
                    },
                    { 
                        icon: "", 
                        title: "품질검증",
                        index: "14",
                        id:"nav_qv",
                        href:"#tab_qv",
                        children:[],
                    },
                ]
            },
            { 
                icon: "", 
                title: "품질검증 결과",
                index: "15",
                href:"",
                id:"navDqSub3",
                children: [
                    { 
                        icon: "", 
                        title: "검증항목별 결과",
                        index: "16",
                        id:"nav_rvi",
                        href:"#tab_rvi",
                        children:[],
                    },
                    { 
                        icon: "", 
                        title: "테이블별 결과",
                        index: "17",
                        id:"nav_dqqvrt",
                        href:"#tab_dqqvrt",
                        children:[],
                    },
                ]
            },
        ]
    },
    {
        icon:"app_registration", 
        title:"관리",
        index: "18",
        href:"",
        id:"mmGroup",
        active:false,
        children: [
            { 
                icon: "", 
                title: "사용자" ,
                index: "19",
                id:"nav_user",
                href:"#tab_user",
                children:[],
            },
            { 
                icon: "", 
                title: "역할 및 권한",
                index: "20",
                id:"nav_roles",
                href:"#tab_roles",
                children:[],
            },
            { 
                icon: "", 
                title: "승인",
                index: "21",
                id:"nav_approval",
                href:"#tab_approval",
                children:[],
            },
            { 
                icon: "", 
                title: "시스템",
                index: "22",
                id:"nav_system",
                href:"#tab_system",
                children:[],
            }
        ]
    },
    {
        icon:"assessment", 
        title:"분석",
        index: "23",
        href:"",
        id:"andGroup",
        active:false,
        children: [
            { 
                icon: "", 
                title: "데이터 표준 현황",
                index: "24",
                id:"nav_scurrent",
                href:"#tab_scurrent",
                children:[],
            },
            { 
                icon: "", 
                title: "데이터 품질 현황",
                index: "25",
                id:"nav_qcurrent",
                href:"#tab_qcurrent",
                children:[],
            },
        ]
    },
];

export default navigationItem;
--
-- PostgreSQL database dump
--

-- Dumped from database version 13.10
-- Dumped by pg_dump version 13.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: tb_user; Type: TABLE DATA; Schema: quality; Owner: admin
--

COPY quality.tb_user (user_id, pwd, nm, email, adm_yn, cret_dt, updt_dt, block_time, login_fail_count, del_yn, phone) FROM stdin;
kjh	1000:09a753b02242807d64b588d2644ac595958a3e576acbb82e:154eba52ed2ef08eea296e71037a9e3b2dfed25b8dcbd9b7	김지호	44@f.com	f	20230331144522	20230331144522	\N	0	t	\N
test1111	1000:163e826325e35ea59bbc50d451fcf9f237e7e5ebb94896d2:6054ddcfafb6bf3d0c9d9b0a979868b87ce15f6b0b997327	김민희	d@dd.cm	f	20230331154702	20230331154702	\N	0	t	\N
test	1000:50ef7b08c8dc12a32f6332a6e857547f672987b3b7bc5f89:a662c3b9ec8fd03745f8cf88494e6e33c1ad86948da9f576	김민희	1@a.com	f	20230331140042	20230331140042	\N	0	t	\N
test1	1000:d0e02f002f4c64bbdcf3f80a7c78c9bbd8412b3ca9d66d91:2f1df08068f0a759f16b25a4d67ed8defe383b211ac0a49f	김민희	aaaaaaaaa@f.com	f	20230331140141	20230331140141	\N	0	t	\N
mhkim	1000:176691aff8275ac0bf097ccfe48ef719e1e643291ac57f94:86441e7be279e40ac4a94e3522240e3c579b79817908f924	mhkim	mhkim@naraedata.com	f	20220524103656	20220524103656	\N	0	t	\N
space	1000:7f592362ad033ccee411a6c5d9b9e8a413d4e8313aa0cccb:ddf06b5c0e06fa9c12fcdd53a87bd9783a1ac6e24fdd3ec1	\N	wodud0170@gmail.com	f	20220524103656	20220524103656	\N	0	\N	010-8501-344
test1333	1000:422f54a8555f178621d6e8dfdff2b950af97cecf07efdca3:297c32b11a734b408b3b3466f7eb60baea023b12bd570000	김민희	aa@n.com	f	20230331154843	20230331154843	\N	0	t	\N
test2	1000:3ef09e8da0cab088cb5dc1b8838c334de520790aec913ee2:0c34f50cc08e9a105d293dc820024198f5159b459d914393	테스트사용자3	test@test.com	f	20230331152823	20230331152823	\N	0	t	\N
user	1000:0a6626a2b93a32074bd63831e246086a9795f20806654f34:99595a291434aa4899e3146254a825e2d23874c06de7fcd5	김민희	mhkim@naraedata.com	t	20230403082539	20230403082539	\N	1	t	\N
user1	1000:ef1cd60e9d147e8a6e2b8bf79c28935886407b00e3c2fc05:99f59882cc44460ba6cdd2121229edb6743383b2595e4e3c	김민희	mhkim@naraedata.com	t	20230405180904	20230405180904	\N	0	t	\N
user2	1000:6b7ed8751cbe9e5a0919b3dd4bc665b10a76d6068ad41a4b:068559309187d4884c94f5032aca20f547758780af8476f1	김민희	mhkim@naraedata.com	t	20230405180932	20230405180932	\N	0	t	\N
user3	1000:03997a819c126ef47e19255bd62d74461fe64460bbd0a4f6:77d0f0de9fa3d060f8b3bfd0e459b60ebee3c86d277ae5f1	김민희	mhkim@naraedata.com	t	20230405181010	20230405181010	\N	0	t	\N
jyjang	1000:cb7826b012e7bb028700e8accd6f571094880f7c7593c362:af62a0820b5e69252ceb31d5dd2836c933324429017c4d71	장재영	wodud0170@naraedata.com	f	20260311142236	20260311142236	20260402141704	11	\N	\N
\.


--
-- PostgreSQL database dump complete
--


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
-- Name: ndata; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ndata;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: tb_data_source; Type: TABLE; Schema: ndata; Owner: -
--

CREATE TABLE ndata.tb_data_source (
    ds_id character varying(50) NOT NULL,
    dsn character varying(200),
    ds_tp smallint,
    dbms_tp character varying(50),
    driver_nm character varying(200),
    svr_addr character varying(200),
    port character varying(10),
    user_id character varying(100),
    pwd character varying(500),
    charset character varying(50),
    private_key character varying(500),
    db_name character varying(200),
    rm_dir character varying(500),
    conn_props character varying(200),
    secure_yn boolean DEFAULT false,
    conn_test_yn character(1) DEFAULT 'N'::bpchar,
    conn_test_dt timestamp without time zone
);


--
-- Name: tb_data_source pk_tb_data_source; Type: CONSTRAINT; Schema: ndata; Owner: -
--

ALTER TABLE ONLY ndata.tb_data_source
    ADD CONSTRAINT pk_tb_data_source PRIMARY KEY (ds_id);


--
-- PostgreSQL database dump complete
--


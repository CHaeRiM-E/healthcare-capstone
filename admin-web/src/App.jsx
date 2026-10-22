
import { useEffect, useState } from "react";
import {
  collection,
  getDocs,
  doc,
  updateDoc,
  serverTimestamp,
} from "firebase/firestore";
import { auth, db } from "./firebase";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";

import { getDoc } from "firebase/firestore";
import "./App.css";

const menus = ["대시보드", "회원 관리", "오류 신고 관리"];

function App() {
  const [menu, setMenu] = useState("대시보드");
    // Firebase 로그인 상태 및 관리자 권한 확인
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      setAuthLoading(true);
      setAdminUser(null);

      if (!user) {
        setAuthLoading(false);
        return;
      }

      try {
        const adminRef = doc(db, "admins", user.uid);
        const adminSnap = await getDoc(adminRef);

        if (adminSnap.exists() && adminSnap.data().role === "admin") {
          setAdminUser(user);
        } else {
          await signOut(auth);
          setLoginError("관리자 권한이 없는 계정입니다.");
        }
      } catch (error) {
        console.error("관리자 권한 확인 실패:", error);
        await signOut(auth);
        setLoginError("관리자 권한을 확인할 수 없습니다.");
      } finally {
        setAuthLoading(false);
      }
    });

    return () => unsubscribe();
  }, []);
    // 관리자 로그인
  async function handleLogin(e) {
    e.preventDefault();
    setLoginError("");
    setLoginLoading(true);

    try {
      await signInWithEmailAndPassword(
        auth,
        loginEmail.trim(),
        loginPassword
      );
    } catch (error) {
      console.error("로그인 실패:", error);
      setLoginError("이메일 또는 비밀번호를 확인해 주세요.");
    } finally {
      setLoginLoading(false);
    }
  }
    // 관리자 로그인 상태
  const [adminUser, setAdminUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginError, setLoginError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);

  // 회원 관리
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  // 오류 신고 관리
  const [reports, setReports] = useState([]);
  const [reportsLoading, setReportsLoading] = useState(true);
  const [reportsError, setReportsError] = useState("");
  const [updatingId, setUpdatingId] = useState(null);

  // Firebase 회원 불러오기
  useEffect(() => {
    async function loadMembers() {
      try {
        const snapshot = await getDocs(
          collection(db, "users")
        );

        const data = snapshot.docs.map((doc) => ({
          id: doc.id,
          ...doc.data(),
        }));

        setMembers(data);
      } catch (err) {
        console.error("회원 조회 오류:", err);
        setError("회원 정보를 불러오지 못했습니다.");
      } finally {
        setLoading(false);
      }
    }

       if (adminUser) {
      loadMembers();
    }
  }, [adminUser]);  

  // Firebase 오류 신고 불러오기
  useEffect(() => {
    async function loadReports() {
      try {
        const snapshot = await getDocs(
          collection(db, "errorReports")
        );

        const data = snapshot.docs.map((doc) => ({
          id: doc.id,
          ...doc.data(),
        }));

        data.sort((a, b) => {
          const aTime = a.createdAt?.toMillis?.() || 0;
          const bTime = b.createdAt?.toMillis?.() || 0;
          return bTime - aTime;
        });

        setReports(data);
      } catch (err) {
        console.error("오류 신고 조회 실패:", err);
        setReportsError("오류 신고를 불러오지 못했습니다.");
      } finally {
        setReportsLoading(false);
      }
    }

    if (adminUser) {
      loadReports();
    }
  }, [adminUser]);
  async function completeReport(reportId) {
  if (updatingId) return;

  const confirmed = window.confirm(
    "이 오류 신고를 처리 완료하시겠습니까?"
  );

  if (!confirmed) return;

  setUpdatingId(reportId);

  try {
    await updateDoc(doc(db, "errorReports", reportId), {
      status: "completed",
      completedAt: serverTimestamp(),
    });

    setReports((prev) =>
      prev.map((report) =>
        report.id === reportId
          ? { ...report, status: "completed" }
          : report
      )
    );

    alert("처리 완료되었습니다!");
  } catch (err) {
    console.error("처리 상태 변경 오류:", err);
    alert("처리 상태 저장에 실패했습니다. Firebase 권한을 확인해 주세요.");
  } finally {
    setUpdatingId(null);
  }
}

const pendingReports = reports.filter(
  (report) => report.status !== "completed"
).length;


  // 회원 검색
  const filteredMembers = members.filter((member) => {
    const keyword = search.toLowerCase();

    return (
      (member.name || "").toLowerCase().includes(keyword) ||
      (member.email || "").toLowerCase().includes(keyword)
    );
  });
    // 로그인 상태 확인 중
  if (authLoading) {
    return <p style={{ textAlign: "center", marginTop: "100px" }}>
      관리자 인증 확인 중...
    </p>;
  }

  // 로그인하지 않은 경우
  if (!adminUser) {
    return (
      <div style={{
        maxWidth: "380px",
        margin: "120px auto",
        padding: "30px",
        border: "1px solid #ddd",
        borderRadius: "12px",
        textAlign: "center"
      }}>
        <h2>💙 건강관리 관리자 로그인</h2>

        <form onSubmit={handleLogin}>
          <input
            type="email"
            placeholder="관리자 이메일"
            value={loginEmail}
            onChange={(e) => setLoginEmail(e.target.value)}
            required
            style={{ width: "100%", padding: "12px", marginBottom: "12px", boxSizing: "border-box" }}
          />

          <input
            type="password"
            placeholder="비밀번호"
            value={loginPassword}
            onChange={(e) => setLoginPassword(e.target.value)}
            required
            style={{ width: "100%", padding: "12px", marginBottom: "12px", boxSizing: "border-box" }}
          />

          {loginError && <p style={{ color: "red" }}>{loginError}</p>}

          <button type="submit" disabled={loginLoading}>
            {loginLoading ? "로그인 중..." : "로그인"}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="admin">
      <aside className="sidebar">
        <h2>💙 건강관리 관리자</h2>
        <p>Admin Dashboard</p>

        {menus.map((item) => (
          <button
            key={item}
            className={menu === item ? "active" : ""}
            onClick={() => setMenu(item)}
          >
            {item}
          </button>
        ))}
      </aside>

      <main className="content">
        <header>
          <h1>{menu}</h1>
          <span>관리자 페이지</span>
        </header>

   
{menu === "대시보드" && (
  <div className="cards">
    <div className="card">
      <h3>전체 회원</h3>
      <strong>
        {loading
          ? "불러오는 중"
          : error
          ? "조회 실패"
          : `${members.length}명`}
      </strong>
    </div>

    <div className="card">
      <h3>전체 오류 신고</h3>
      <strong>
        {reportsLoading
          ? "불러오는 중"
          : reportsError
          ? "조회 실패"
          : `${reports.length}건`}
      </strong>
    </div>

    <div className="card">
      <h3>미처리 신고</h3>
      <strong>
        {reportsLoading
          ? "불러오는 중"
          : reportsError
          ? "조회 실패"
          : `${pendingReports}건`}
      </strong>
    </div>
  </div>
)}


        {menu === "회원 관리" && (
          <section className="panel">
            <h2>회원 목록</h2>

            <input
              type="text"
              placeholder="이름 또는 이메일 검색"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{
                width: "100%",
                padding: "12px",
                marginBottom: "20px",
                border: "1px solid #ddd",
                borderRadius: "8px",
              }}
            />

            {loading ? (
              <p>회원 정보를 불러오는 중...</p>
            ) : error ? (
              <p style={{ color: "red" }}>{error}</p>
            ) : (
              <>
                <p>총 {filteredMembers.length}명</p>

                <table
                  style={{
                    width: "100%",
                    borderCollapse: "collapse",
                    textAlign: "left",
                  }}
                >
                  <thead>
                    <tr>
                      <th style={{ padding: "12px" }}>이름</th>
                      <th style={{ padding: "12px" }}>이메일</th>
                    </tr>
                  </thead>

                  <tbody>
                    {filteredMembers.map((member) => (
                      <tr key={member.id}>
                        <td
                          style={{
                            padding: "12px",
                            borderTop: "1px solid #eee",
                          }}
                        >
                          {member.name || "이름 없음"}
                        </td>

                        <td
                          style={{
                            padding: "12px",
                            borderTop: "1px solid #eee",
                          }}
                        >
                          {member.email || "이메일 없음"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {filteredMembers.length === 0 && (
                  <p>조회된 회원이 없습니다.</p>
                )}
              </>
            )}
          </section>
        )}

        {menu === "오류 신고 관리" && (
          <section className="panel">
            <h2>오류 신고 목록</h2>

            {reportsLoading ? (
              <p>오류 신고를 불러오는 중...</p>
            ) : reportsError ? (
              <p style={{ color: "red" }}>
                {reportsError}
              </p>
            ) : (
              <>
                <p>총 {reports.length}건</p>

                <table
                  style={{
                    width: "100%",
                    borderCollapse: "collapse",
                    textAlign: "left",
                  }}
                >
             
<thead>
  <tr>
    <th style={{ padding: "12px", textAlign: "left" }}>
      신고 유형
    </th>
    <th style={{ padding: "12px", textAlign: "left" }}>
      오류 내용
    </th>
    <th style={{ padding: "12px", textAlign: "left" }}>
      신고 날짜
    </th>
    <th style={{ padding: "12px", textAlign: "left" }}>
      처리 상태
    </th>
    <th style={{ padding: "12px", textAlign: "left" }}>
      관리
    </th>
  </tr>
</thead>

                  <tbody>
                    {reports.map((report) => (
                      <tr key={report.id}>
                        <td
                          style={{
                            padding: "12px",
                            borderTop: "1px solid #eee",
                          }}
                        >
                          {report.errorType || "미분류"}
                        </td>

                        <td
                          style={{
                            padding: "12px",
                            borderTop: "1px solid #eee",
                          }}
                        >
                          {report.errorContent || "내용 없음"}
                        </td>

                        <td
                          style={{
                            padding: "12px",
                            borderTop: "1px solid #eee",
                          }}
                        >
                          {report.createdAt?.toDate
                            ? report.createdAt
                                .toDate()
                                .toLocaleString("ko-KR")
                            : "날짜 없음"}
                        </td>
                        <td
  style={{
    padding: "12px",
    borderTop: "1px solid #eee",
  }}
>
  {report.status === "completed" ? (
    <span style={{ color: "green", fontWeight: "bold" }}>
      처리 완료
    </span>
  ) : (
    <span style={{ color: "#e67e22", fontWeight: "bold" }}>
      미처리
    </span>
  )}
</td>

<td
  style={{
    padding: "12px",
    borderTop: "1px solid #eee",
  }}
>
  <button
    onClick={() => completeReport(report.id)}
    disabled={
      report.status === "completed" ||
      updatingId !== null
    }
    style={{
      padding: "8px 14px",
      background:
        report.status === "completed" ? "#ccc" : "#4169a1",
      color: "white",
      border: "none",
      borderRadius: "6px",
      cursor:
        report.status === "completed" ? "default" : "pointer",
    }}
  >
    {report.status === "completed"
      ? "완료"
      : updatingId === report.id
      ? "처리 중..."
      : "처리 완료"}
  </button>
</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                {reports.length === 0 && (
                  <p>접수된 오류 신고가 없습니다.</p>
                )}
              </>
            )}
          </section>
        )}
      </main>
    </div>
  );
}

export default App;

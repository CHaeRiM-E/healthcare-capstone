import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";


const firebaseConfig = {
  apiKey:"AIzaSyBrHjScG1W4dz1G0KdNHb-_WM5R-WxpF6U",
  authDomain: "myapplication-b374c.firebaseapp.com",
  projectId: "myapplication-b374c",
  storageBucket: "myapplication-b374c.firebasestorage.app",
  messagingSenderId: "192037094244",
  appId: "1:192037094244:web:4408927b374d33c3767071"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);
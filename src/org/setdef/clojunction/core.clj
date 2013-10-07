(ns org.setdef.clojunction.core
  (:require 
    [clojure.data.json :as json]
    [org.httpkit.client :as http]))


(defrecord ConfluenceHost [^String host ^String context ^Boolean secure ^Boolean verify-cert])

(def ^:const suffix "rpc/json-rpc/confluenceservice-v2")


(defn make-endpoint 
  "Creates an endpoint"
  [& {:keys [host port context secure? skip-cert-verif? creds]
    :or {host "localhost" port nil context "confluence" secure? true skip-cert-verif? false}}]
  (let [schema (if secure? "https" "http")
        port-str (if port (str ":" port) "")
        url (str schema "://" host port-str "/" context "/" suffix)
        res {:url url,:skip-cert-verif? skip-cert-verif?,:creds creds}]
    res))
    
(defn conf-call 
  "Generic confluence call"
  [endpoint method-name & [more-opts]]
  (let [{:keys [url skip-cert-verif? creds]} endpoint 
        the-call (str url "/" method-name)
        ep-opts {:insecure? skip-cert-verif? :basic-auth creds}]
    (let [res (http/post 
                the-call 
                (merge ep-opts {:headers {"Content-Type" "application/json" "Accept" "application/json"}} more-opts)
                (fn [the-resp] 
                  (if (= (quot (:status the-resp) 100) 2) 
                    (assoc the-resp :body-json (json/read-str (:body the-resp) :key-fn clojure.core/keyword))
                    the-resp)))]
      res)))

(defn sysinfo 
 "gets system info"
  [endpoint]
  (conf-call endpoint "getServerInfo"))

(defn spaces
  "gets the list of spaces"
  [endpoint ]
  (conf-call endpoint "getSpaces"))

(defn pages
  "gets the list of pages of an space"
  [endpoint sp-key]
  (let [body (json/write-str (vector sp-key))]
    (conf-call endpoint "getPages" (assoc {} :body body))))

(defn page
  "gets a page from an space or from the global system"
  ([endpoint page-id]
    (let [body (json/write-str (vector page-id))]
      (conf-call endpoint "getPage" (assoc {} :body body))))
  ([endpoint sp-key title]
    (let [body (json/write-str (vector sp-key title))]
      (conf-call endpoint "getPage" (assoc {} :body body)))))

(defn page-children
  "gets children for a page"
  [endpoint page-id]
  (let [body (json/write-str (vector page-id))]
    (conf-call endpoint "getChildren" (assoc {} :body body))))

(defn page-attachments
  "gets attachments for a page"
  [endpoint page-id]
  (let [body (json/write-str (vector page-id))]
    (conf-call endpoint "getAttachments" (assoc {} :body body))))

(defn adj-matrix
  "Generates the adjancency matrix for the page list retrieved by pages.
  The result is a map of sets (pageId->children pageIds). The 
  home page is the child of the pageId 0 page"
  [all-pages]
  (letfn [(reduce-page [tree page]
            (let [pid (:parentId page)
                  id (:id page)
                  prev-siblings (get tree pid #{})
                  all-siblings (conj prev-siblings id) ]
      (assoc tree pid all-siblings)))]
    (reduce reduce-page {} all-pages)))

(defn edges
  "Generates a sequence of pairs of parent-son pageIds that
  could be used to build a page tree"
  [adj]
  (letfn [(children [entry] (get adj entry))
          (gen-pairs [parent child-pages] (map #(vector parent %1) child-pages))
          (aux [queue res] 
            (let [h (peek queue)]
              (if (not (nil? h))
                (let [child-pages (children h)
                      pairs (gen-pairs h child-pages)
                      next-queue (reduce conj (pop queue) child-pages)
                      next-res (reduce conj res pairs)]
                  (aux next-queue next-res))
                res)))]
    (let [ini-queue (-> (clojure.lang.PersistentQueue/EMPTY) (conj 0))]
      (aux ini-queue (vector))))) 

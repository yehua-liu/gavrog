(ns org.gavrog.clojure.simplify3d
  (:use (org.gavrog.clojure
          delaney
          fundamental)))

(defn merge-volumes [ds]
  (let [idx (last (indices ds))]
    (collapse ds idx (for [[D i] (inner-edges ds) :when (= i idx)
                           E [D (s ds i D)]]
                       E))))

(defn merge-facets [ds]
  (let [[i j] (drop (dec (dim ds)) (indices ds))]
    (collapse ds i (for [D (orbit-reps ds [i j]) :when (= 2 (m ds i j D))
                         E (orbit-elements ds [i j] D)]
                     E))))

(defn contract-edges [ds]
  (-> ds dual merge-volumes dual))

(defn squish-digons [ds]
  (-> ds dual merge-facets dual))

(defn- fix-degree-1-vertex [ds]
  (if-let [C (first (filter #(= 1 (m ds 1 2 %)) (orbit-reps ds [1 2])))]
    (let [D (walk ds C 1 0)
          E (walk ds C 0 1)
          [F G] (map #(s ds 3 %) [D E])
          [D* E* F* G*] (map #(s ds 1 %) [D E F G])
          ops* (ops ds)
          ops* (assoc ops* 1 (conj (ops* 1)
                                   [D E*] [E D*] [D* E] [E* D]
                                   [F G*] [G F*] [F* G] [G* F]))
          tmp (DSymbol. 3 (size ds) ops* (vs ds))]
      (collapse tmp 3 (orbit-elements tmp [0 1 3] C)))
    ds))

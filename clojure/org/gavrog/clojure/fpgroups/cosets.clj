(ns org.gavrog.clojure.fpgroups.cosets
  (:use (org.gavrog.clojure.fpgroups
          free-word)
        (org.gavrog.clojure.common
          arithmetic
          util
          partition
          generators)))

(defn- merge-rows [table equiv q a b]
  (let [merge (fn [ra rb]
                (reduce (fn [r g] (if (ra g) r (assoc r g (rb g))))
                        ra (keys rb)))
        row-a (merge (table a) (table b))
        row-b (merge (table b) (table a))
        q (into q (for [g (keys row-a)
                        :when (not= (equiv (row-a g)) (equiv (row-b g)))]
                    [(row-a g) (row-b g)]))
        table (conj table [a row-a] [b row-a])]
    [table q]))

(defn- identify [table equiv i j]
  (loop [q (conj empty-queue [i j])
         table table
         equiv equiv]
    (if-let [[i j] (first q)]
      (let [q (pop q)
            a (equiv i)
            b (equiv j)]
        (if (= a b)
          (recur q table equiv)
          (let [equiv (conj equiv [a b])
                [table q] (merge-rows table equiv q a b)]
            (recur q table equiv))))
      [table equiv])))

(defn- scan [table equiv w start]
  (let [n (count w)
        [head a] (loop [row start, i 0]
                   (if (>= i n)
                     [row i]
                     (if-let [next ((table row) (get w i))]
                       (recur next (inc i))
                       [row i])))
        [tail b] (loop [row start, i n]
                   (if (<= i a)
                     [row i]
                     (if-let [next ((table row) (- (get w (dec i))))]
                       (recur next (dec i))
                       [row i])))]
    (cond (= (inc a) b)
          [(-> table
             (assoc-in [head (get w a)] tail)
             (assoc-in [tail (- (get w a))] head))
           equiv
           head]
          
          (and (= a b) (not= head tail))
          (identify table equiv head tail)
          
          :else
          [table equiv])))

(defn- scan-relations [rels subgens table equiv n]
  (let [[table equiv]
        (reduce (fn [[t p] w] (scan t p w n)) [table equiv] rels)]
    (reduce (fn [[t p] w] (scan t p w (equiv 0))) [table equiv] subgens)))

(defn- compressed-table [table equiv]
  (let [reps (into (sorted-set) (filter #(= % (equiv %)) (keys table)))
        rep-to-idx (into {} (map vector reps (range)))
        canon (comp rep-to-idx equiv)]
    (into {} (for [[i row] table :when (reps i)]
               [(canon i) (into {} (map (fn [[j v]] [j (canon v)]) row))]))))

(defn- maybe-compressed [[table equiv] factor]
  (let [nr-invalid (- (reduce + (map count equiv)) (count equiv))]
    (if (> nr-invalid (* factor (count table)))
      (compressed-table [table equiv])
      [table equiv])))

(defn coset-table [nr-gens relators subgroup-gens]
  (let [with-inverses (fn [ws] (vec (into #{} (concat ws (map inverse ws)))))
        gens (vec (concat (range 1 (inc nr-gens))
                          (range -1 (- (inc nr-gens)) -1)))
        rels (with-inverses (for [w relators, i (range (count w))]
                              (into (subvec w i) (subvec w 0 i))))
        subgens (with-inverses subgroup-gens)]
    (loop [table {0 {}}
           equiv pempty
           i 0
           j 0]
      (assert (< (count table) 10000))
      (cond (>= i (count table))
            (compressed-table table equiv)
            
            (or (>= j (count gens)) (not= i (equiv i)))
            (recur table equiv (inc i) 0)
            
            ((table i) (get gens j))
            (recur table equiv i (inc j))
            
            :else
            (let [g (get gens j)
                  g* (- g)
                  n (count table)
                  table (-> table (assoc-in [i g] n) (assoc-in [n g*] i))
                  [table equiv] (maybe-compressed
                                  (scan-relations rels subgens table equiv n)
                                  1/2)]
              (recur table equiv i (inc j)))))))

(defn coset-representatives [table]
  (loop [q (conj empty-queue 0)
         reps {0 []}]
    (if-let [i (first q)]
      (let [row (table i)
            free (filter (comp nil? reps second) row)
            reps (into reps (map (fn [[g k]] [k (-* (reps i) [g])]) free))
            q (into (pop q) (map second free))]
        (recur q reps))
      reps)))

(defn- compare-renumbered-from [table gens start]
  (loop [o2n {start 0}, n2o {0 start}, row 0, col 0]
    (assert (or (< row (count o2n)) (>= row (count table)))
            (str table " is not transitive."))
    ; (println "o2n =" o2n " n2o =" n2o " row =" row " col =" col) 
    (cond (>= row (count table)) 0
          (>= col (count gens)) (recur o2n n2o (inc row) 0)
          :else
          (let [oval ((table row) (gens col))
                nval (and (n2o row) ((table (n2o row)) (gens col)))
                [o2n n2o] (if (and nval (nil? (o2n nval)))
                            [(assoc o2n nval (count o2n))
                             (assoc n2o (count n2o) nval)]
                            [o2n
                             n2o])
                nval (o2n nval)]
            (cond (= oval nval) (recur o2n n2o row (inc col))
                  (nil? oval) -1
                  (nil? nval) 1
                  :else (- nval oval))))))

(defn- canonical-table [table gens]
  (every? (fn [start] (not (neg? (compare-renumbered-from table gens start))))
          (range 1 (count table))))

(defn- expand-generators [nr-gens]
  (vec (concat (range 1 (inc nr-gens))
               (range -1 (- (inc nr-gens)) -1))))

(defn- expand-relators [relators]
  (let [with-inverses (fn [ws] (vec (into #{} (concat ws (map inverse ws)))))]
    (with-inverses (for [w relators, i (range (count w))]
                     (into (subvec w i) (subvec w 0 i))))))

(defn- free-in-table [table gens]
  (for [k (range (count table))
        :let [row (table k)]
        g gens :when (nil? (row g))]
    [k g]))

(defn- scan-recursively [rels table k]
  (loop [q empty-queue, k k, t table, rs rels]
    (cond (seq rs)
          (let [[t equiv n] (scan t pempty (first rs) k)]
            (if (seq equiv)
              nil
              (recur (if (nil? n) q (conj q n)) k t (rest rs))))
          
          (seq q)
          (recur (pop q) (first q) t rels)
          
          :else
          t)))

(defn- potential-children [table gens rels max-cosets]
  (when-let [[k g] (first (free-in-table table gens))]
    (let [g* (- g)
          n (count table)
          matches (filter (fn [k] (nil? ((table k) g*))) (range k n))
          candidates (if (< n max-cosets) (conj matches n) matches)]
      (for [k* candidates]
        (let [t (-> table (assoc-in [k g] k*) (assoc-in [k* g*] k))]
          (scan-recursively rels t k))))))

(defn table-generator [nr-gens relators max-cosets]
  (let [gens (expand-generators nr-gens)
        rels (expand-relators relators)
        free (fn [t] (free-in-table t gens))]
    (make-backtracker
      {:root {0 {}}
       :extract (fn [table] (when (empty? (free table)) table))
       :children (fn [table]
                   (for [t
                         (potential-children table gens rels max-cosets)
                         :when (and (not (nil? t)) (canonical-table t gens))]
                     t))})))

(defn- induced-table [gens img img0]
  (loop [table {0 {}}, o2n {img0 0}, n2o {0 img0}, i 0, j 0]
    (cond (>= i (count table)) table
          (>= j (count gens)) (recur table o2n n2o (inc i) 0)
          :else
          (let [g (gens j)
                k (img (n2o i) g)
                n (or (o2n k) (count table))
                o2n (assoc o2n k n)
                n2o (assoc n2o n k)
                table (-> table (assoc-in [i g] n) (assoc-in [n (- g)] i))]
            (recur table o2n n2o i (inc j))))))

(defn intersection-table [table-a table-b]
  (induced-table (vec (keys (or (table-a 0) {})))
                 (fn [[a b] g] [((table-a a) g) ((table-b b) g)])
                 [0 0]))

(defn core-table [table]
  (let [elms (vec (keys table))]
    (induced-table (vec (keys (or (table 0) {})))
                   (fn [es g] (vec (map (fn [e] ((table e) g)) es)))
                   elms)))

(defn- relator-as-row [nr-gens w]
  (reduce (fn [r x] (assoc r (abs x) (+ (r (abs x)) (sign x))))
          (into {} (for [g (range 1 (inc nr-gens))] [g 0]))
          w))

(defn- relator-matrix [nr-gens relators]
  (into {} (for [k (range (count relators))
                 [g n] (relator-as-row nr-gens (nth relators k))]
             [[k g] n])))

(defn abelian-invariants [nr-gens relators]
  (let [rows (range (count relators))
        cols (range 1 (inc nr-gens))
        D (diagonalized (relator-matrix nr-gens relators) rows cols false)
        indices (map vector rows cols)
        d (map (partial get D) indices)]
    (concat (repeat (- nr-gens (count d)) 0)
            (->> (abelian-factors d) (filter (partial not= 1)) sort))))


;; ---- TODO turn these into tests

(defn- print-table [t]
  (doseq [[key row] (sort t)]
            (println key row))
          (println))

(defn- debug-generator []
  (let [nr-gens 4
        rels [[1 1] [2 2] [3 3] [4 4] [1 3 1 3] [1 4 1 4] [2 4 2 4]]
        max-cosets 8

        generator (table-generator nr-gens rels max-cosets)
  
        table {0 {1 1 -1 1 2 2 -2 2 3 3 -3 3 4 4 -4 4}
               1 {1 0 -1 0 2 5 -2 5 3 6 -3 6 4 7 -4 7}
               2 {1 5 -1 5 2 0 -2 0 3 7 -3 7 4 6 -4 6}
               3 {1 6 -1 6 2 7 -2 7 3 0 -3 0 4 5 -4 5}
               4 {1 7 -1 7 2 6 -2 6 3 5 -3 5 4 0 -4 0}
               5 {1 2 -1 2 2 1 -2 1 3 4 -3 4 4 3 -4 3}
               6 {1 3 -1 3 2 4 -2 4 3 1 -3 1 4 2 -4 2}
               7 {1 4 -1 4 2 3 -2 3 3 2 -3 2 4 1 -4 1}}
        
        contains?
        (fn [t s] (every? identity (for [[key row] s [gen val] row]
                                     (= val ((t key) gen)))))
        
        steps (traverse generator (partial contains? table))]

    (doseq [gen steps] (print-table (current gen)))
    (println "---")
    (doseq [gen (traverse (sub-generator (last steps)))]
      (print-table (current gen))
      (doseq [[t equiv] (potential-children (current gen)
                                 (expand-generators nr-gens)
                                 (expand-relators rels)
                                 max-cosets)
              :when (contains? table t)]
        (println "Equivalences:" (seq equiv))
        (print-table t)))))

(defn- debug-scanrel []
  (let [gens (expand-generators 4)
        rels (expand-relators
               [[1 1] [2 2] [3 3] [4 4] [1 3 1 3] [1 4 1 4] [2 4 2 4]])
        max-cosets 8
        
        table {0 {1 1 -1 1 2 2 -2 2 3 3 -3 3 4 4 -4 4}
               1 {1 0 -1 0 2 5 -2 5 3 6 -3 6 4 7 -4 7}
               2 {1 5 -1 5 2 0 -2 0 3 7 -3 7 4 6 -4 6}
               3 {1 6 -1 6          3 0 -3 0         }
               4 {1 7 -1 7          3 5 -3 5 4 0 -4 0}
               5 {1 2 -1 2 2 1 -2 1 3 4 -3 4         }
               6 {1 3 -1 3          3 1 -3 1 4 2 -4 2}
               7 {1 4 -1 4          3 2 -3 2 4 1 -4 1}}
        
        start 2]
    
    (print-table (scan-recursively rels table start))))


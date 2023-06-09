package ed.inf.adbs.minibase.evaluator;

import base.*;
import ed.inf.adbs.minibase.structures.Tuple;
import ed.inf.adbs.minibase.structures.TypeWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SumOperator extends Operator{
    /**
     *             child:  the logic child of the SumOperator
     *         group_map:  stores the SUM value of the certain Tuple
     *                     (the list of TypeWrapper is output of its child after group-by)
     * group_column_list:  indicate a list of column indexes needed to group by
     *   agg_column_list:  indicate a list of column indexes needed to aggregate
     *     retrieve_list:  store the list of tuples dumped by child
     * */
    private final Operator child;
    private final HashMap<List<TypeWrapper>, Integer> group_map;
    List<Integer> group_column_list, agg_column_list;
    private final List<List<TypeWrapper>> retrieve_list; // store the key of group_map for getNextTuple
    public SumOperator(Operator child, Head head) throws IOException {
        /**
         * constructor
         * store the child dump in the retrieve_list. Then parse the tuple list according to
         * the aggregate terms in head
         * @params:
         *  child: logic child of the operation tree
         *   head:  query head
         * */
        SumAggregate aggregate = head.getSumAggregate();
        this.child = child;
        List<Variable> head_variables = head.getVariables(); // get head variables
        List<Tuple> dump_list = new ArrayList<>(child.dump()); // all the tuple by child
        List<Term> relation_body = getRelation_atom().getTerms(); // terms in relation (compare with head variables)
        retrieve_list = new ArrayList<>();
        group_map = new HashMap<>();
        group_column_list = new ArrayList<>();
        agg_column_list = new ArrayList<>();
        // stats the group by terms
        for (Variable head_var: head_variables){
            // [1, 3] means we need to group by the 1st and 3rd columns
            group_column_list.add(relation_body.indexOf(head_var));
        }
        // parse the aggregate
        List<Term> agg_terms = aggregate.getProductTerms(); // get the aggregate products for processing
        Integer mul_const = 1; // use a mul to represent the results of all constant multiply
        for (Term term: agg_terms){
            if (term instanceof IntegerConstant) {
                mul_const *= ((IntegerConstant) term).getValue();
            }
            else { // find the column index corresponding to the term
                // assuming var in head must be in relation_body, its child's or sql writer's responsibility
                // to examine the format
                // e.g. Q(x,y,SUM(z)):- R(x,z),S(u,v) should not come to this step
                agg_column_list.add(relation_body.indexOf(term));
            }
        }

        for (Tuple tuple: dump_list){ // group by columns
            List<TypeWrapper> group_mark_list = new ArrayList<>();
            for (int index:group_column_list){ // Q(x,y,SUM(z*z)) -> save the list of [x, y]
                group_mark_list.add(tuple.getWrapInTuple(index));
            }
            int mul = 1; // a local mul for the corresponding variable mul value
            for (int index:agg_column_list){ // Q(x,y,SUM(z)) -> save the list of [z*z]
                mul *= (Integer) tuple.getWrapInTuple(index).getValue();
            }
            // then use group_mark_list as hash key
            if(group_map.containsKey(group_mark_list)){
                group_map.put(group_mark_list, group_map.get(group_mark_list) + mul_const * mul);
            }
            else {
                group_map.put(group_mark_list, mul_const * mul);
                retrieve_list.add(group_mark_list); // group_map.size() == retrieve_list.size()
            }
        }

    }

    @Override
    public void reset() throws IOException {
        child.reset();
    }

    @Override
    public Tuple getNextTuple(){
        /**
         * get the next Tuple, while the group map is not empty
         * @return:
         *   null:  if not find the next tuple
         *  tuple:  if group_map not empty
         * */
        if (!group_map.isEmpty()) {
            // get the group of list of columns as key (group by)
            List<TypeWrapper> key_group = retrieve_list.remove(0);
            Tuple new_tuple = new Tuple(getRelation_atom().getName());
            new_tuple.add(key_group);
            // find the aggregate value of the corresponding column list
            new_tuple.tupleProjection(new TypeWrapper((int) group_map.remove(key_group)));
            return new_tuple;
        }
        return null;
    }

    @Override
    public RelationalAtom getRelation_atom() {
        return child.getRelation_atom();
    }

    @Override
    public BufferedReader getBr() {
        return null;
    }
}
